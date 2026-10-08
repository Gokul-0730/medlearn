-- ====================================================================
-- MedLearn Supabase Authentication & User Profiles Schema
-- Security: RLS enforced, Password handled exclusively by Supabase Auth
-- ====================================================================

-- 1. Create public profiles table linked to Supabase auth.users
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT username_min_length CHECK (LENGTH(TRIM(username)) >= 3),
    CONSTRAINT username_format CHECK (username ~ '^[a-zA-Z0-9_]+$')
);

-- 2. Case-insensitive unique index for usernames
CREATE UNIQUE INDEX IF NOT EXISTS profiles_username_lower_idx ON public.profiles (LOWER(username));

-- 3. Automatic updated_at trigger timestamp handler
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_profiles_updated_at ON public.profiles;
CREATE TRIGGER set_profiles_updated_at
BEFORE UPDATE ON public.profiles
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 5. Strict RLS Policies (Users can only read & update their OWN profile)
DROP POLICY IF EXISTS "Users can view own profile" ON public.profiles;
CREATE POLICY "Users can view own profile"
ON public.profiles
FOR SELECT
USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
ON public.profiles
FOR UPDATE
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
CREATE POLICY "Users can insert own profile"
ON public.profiles
FOR INSERT
WITH CHECK (auth.uid() = id);

-- 6. Trigger to automatically provision user profile upon auth.users creation
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_raw_username TEXT;
    v_username TEXT;
    v_full_name TEXT;
BEGIN
    -- Read metadata provided during Supabase signUp({ options: { data: { ... } } })
    v_raw_username := NEW.raw_user_meta_data->>'username';
    v_full_name := COALESCE(NEW.raw_user_meta_data->>'full_name', '');

    IF v_raw_username IS NOT NULL AND LENGTH(TRIM(v_raw_username)) >= 3 THEN
        v_username := LOWER(TRIM(v_raw_username));
    ELSE
        -- Fallback username derived from email or UUID
        v_username := LOWER(SPLIT_PART(NEW.email, '@', 1) || '_' || SUBSTRING(NEW.id::text, 1, 5));
    END IF;

    INSERT INTO public.profiles (id, username, email, full_name, avatar_url, created_at, updated_at)
    VALUES (
        NEW.id,
        v_username,
        NEW.email,
        v_full_name,
        NULL,
        NOW(),
        NOW()
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_user();

-- 7. Secure RPC to check username availability without exposing identities or tables
CREATE OR REPLACE FUNCTION public.check_username_available(p_username TEXT)
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_username IS NULL OR LENGTH(TRIM(p_username)) < 3 OR LENGTH(TRIM(p_username)) > 30 THEN
        RETURN FALSE;
    END IF;

    RETURN NOT EXISTS (
        SELECT 1 FROM public.profiles WHERE LOWER(username) = LOWER(TRIM(p_username))
    );
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION public.check_username_available(TEXT) TO anon, authenticated;

-- 8. Secure RPC for resolving Username to Email for Login
-- Runs with SECURITY DEFINER to lookup only the exact matching email.
-- Does NOT expose passwords, user lists, or profiles to unauthorized queries.
CREATE OR REPLACE FUNCTION public.get_email_for_username(p_username TEXT)
RETURNS TEXT
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
BEGIN
    SELECT email INTO v_email
    FROM public.profiles
    WHERE LOWER(username) = LOWER(TRIM(p_username))
    LIMIT 1;

    RETURN v_email;
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION public.get_email_for_username(TEXT) TO anon, authenticated;

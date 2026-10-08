import { getSupabaseBrowserClient } from './client';
import { Profile, PasswordStrengthResult } from './auth-types';

/**
 * Username Validation Rules:
 * - 3 to 30 characters
 * - Letters, numbers, and underscores only
 * - No spaces
 */
export function validateUsername(username: string): { isValid: boolean; error?: string } {
  const trimmed = username.trim();
  if (!trimmed) {
    return { isValid: false, error: 'Username is required.' };
  }
  if (trimmed.length < 3) {
    return { isValid: false, error: 'Username must be at least 3 characters.' };
  }
  if (trimmed.length > 30) {
    return { isValid: false, error: 'Username must be no more than 30 characters.' };
  }
  if (/\s/.test(username)) {
    return { isValid: false, error: 'Username cannot contain spaces.' };
  }
  const validPattern = /^[a-zA-Z0-9_]+$/;
  if (!validPattern.test(trimmed)) {
    return {
      isValid: false,
      error: 'Username must contain only letters, numbers, and underscores.',
    };
  }
  return { isValid: true };
}

/**
 * Email format validation
 */
export function validateEmail(email: string): { isValid: boolean; error?: string } {
  const trimmed = email.trim();
  if (!trimmed) {
    return { isValid: false, error: 'Email is required.' };
  }
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailPattern.test(trimmed)) {
    return { isValid: false, error: 'Please enter a valid email address.' };
  }
  return { isValid: true };
}

/**
 * Password Strength Evaluation
 * Rules:
 * - Minimum 8 characters
 * - Uppercase, Lowercase, Number, Special Character
 */
export function evaluatePasswordStrength(password: string): PasswordStrengthResult {
  const hasMinLength = password.length >= 8;
  const hasUppercase = /[A-Z]/.test(password);
  const hasLowercase = /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);

  let score = 0;
  if (hasMinLength) score += 1;
  if (hasUppercase && hasLowercase) score += 1;
  if (hasNumber) score += 1;
  if (hasSpecial) score += 1;

  let label: PasswordStrengthResult['label'] = 'Weak';
  let color = '#ef4444'; // red

  if (score === 4) {
    label = 'Strong';
    color = '#10b981'; // emerald
  } else if (score === 3) {
    label = 'Good';
    color = '#0ea5e9'; // sky
  } else if (score === 2) {
    label = 'Fair';
    color = '#f59e0b'; // amber
  }

  return {
    score,
    label,
    hasMinLength,
    hasUppercase,
    hasLowercase,
    hasNumber,
    hasSpecial,
    color,
  };
}

/**
 * Check username availability via secure RPC function
 */
export async function checkUsernameAvailability(username: string): Promise<{ available: boolean; error?: string }> {
  const validation = validateUsername(username);
  if (!validation.isValid) {
    return { available: false, error: validation.error };
  }

  const supabase = getSupabaseBrowserClient();
  try {
    const { data, error } = await supabase.rpc('check_username_available', {
      p_username: username.trim().toLowerCase(),
    });

    if (error) {
      // Fallback check if RPC is not yet created
      const { data: profileData } = await supabase
        .from('profiles')
        .select('username')
        .ilike('username', username.trim())
        .maybeSingle();

      return { available: !profileData };
    }

    return { available: Boolean(data) };
  } catch {
    return { available: false, error: 'Unable to check username availability.' };
  }
}

/**
 * Register a new user with Username, Email, Password, and Full Name
 * Passwords are sent directly to Supabase Auth - NEVER stored in profiles or plain text.
 */
export async function registerUser({
  username,
  email,
  password,
  fullName,
}: {
  username: string;
  email: string;
  password: string;
  fullName?: string;
}): Promise<{ success: boolean; error?: string; requiresEmailConfirmation?: boolean }> {
  // 1. Validate inputs
  const userValid = validateUsername(username);
  if (!userValid.isValid) return { success: false, error: userValid.error };

  const emailValid = validateEmail(email);
  if (!emailValid.isValid) return { success: false, error: emailValid.error };

  const strength = evaluatePasswordStrength(password);
  if (!strength.hasMinLength) {
    return { success: false, error: 'Password must be at least 8 characters long.' };
  }

  const supabase = getSupabaseBrowserClient();

  // 2. Check if username is already taken
  const avail = await checkUsernameAvailability(username);
  if (!avail.available) {
    return { success: false, error: 'Username is already taken. Please choose another.' };
  }

  // 3. Register user with Supabase Auth
  // User metadata includes username and full_name so the database trigger can populate public.profiles
  const cleanUsername = username.trim().toLowerCase();
  const redirectOrigin =
    process.env.NEXT_PUBLIC_APP_URL ||
    (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:3000');

  const { data, error } = await supabase.auth.signUp({
    email: email.trim(),
    password,
    options: {
      data: {
        username: cleanUsername,
        full_name: fullName?.trim() || cleanUsername,
      },
      emailRedirectTo: `${redirectOrigin.replace(/\/$/, '')}/`,
    },
  });

  if (error) {
    if (error.message.toLowerCase().includes('already registered')) {
      return { success: false, error: 'An account with this email already exists.' };
    }
    return { success: false, error: error.message };
  }

  // 4. Fallback manual profile insertion if trigger is not installed in database yet
  if (data?.user) {
    try {
      await supabase.from('profiles').upsert(
        {
          id: data.user.id,
          username: cleanUsername,
          email: email.trim(),
          full_name: fullName?.trim() || cleanUsername,
          updated_at: new Date().toISOString(),
        },
        { onConflict: 'id' }
      );
    } catch {
      // Handled silently if already created by trigger or RLS restricts
    }
  }

  const requiresEmailConfirmation = !data.session;
  return { success: true, requiresEmailConfirmation };
}

/**
 * Secure Login with either Email or Username + Password
 */
export async function loginWithIdentifier({
  identifier,
  password,
}: {
  identifier: string;
  password: string;
}): Promise<{ success: boolean; error?: string }> {
  const cleanIdentifier = identifier.trim();
  if (!cleanIdentifier || !password) {
    return { success: false, error: 'Please enter your username/email and password.' };
  }

  const supabase = getSupabaseBrowserClient();
  let resolvedEmail = cleanIdentifier;

  // If the identifier doesn't contain '@', resolve the username to email securely
  if (!cleanIdentifier.includes('@')) {
    try {
      const { data, error } = await supabase.rpc('get_email_for_username', {
        p_username: cleanIdentifier.toLowerCase(),
      });

      if (error || !data) {
        // Uniform error prevents user enumeration
        return { success: false, error: 'Invalid username or password.' };
      }
      resolvedEmail = data;
    } catch {
      return { success: false, error: 'Invalid username or password.' };
    }
  }

  // Authenticate strictly with Supabase Auth
  const { error } = await supabase.auth.signInWithPassword({
    email: resolvedEmail,
    password,
  });

  if (error) {
    if (error.message.toLowerCase().includes('invalid login credentials')) {
      return { success: false, error: 'Invalid username/email or password.' };
    }
    if (error.message.toLowerCase().includes('email not confirmed')) {
      return { success: false, error: 'Please verify your email before logging in.' };
    }
    return { success: false, error: error.message };
  }

  return { success: true };
}

/**
 * Sign out user and clear session
 */
export async function logoutUser(): Promise<{ success: boolean; error?: string }> {
  const supabase = getSupabaseBrowserClient();
  try {
    const { error } = await supabase.auth.signOut();
    if (error) return { success: false, error: error.message };
    return { success: true };
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Failed to sign out.';
    return { success: false, error: message };
  }
}

/**
 * Fetch current user profile with caching/fallback
 */
export async function fetchUserProfile(userId: string): Promise<Profile | null> {
  const supabase = getSupabaseBrowserClient();
  try {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', userId)
      .maybeSingle();

    if (error || !data) {
      // If profile row does not exist yet, construct from user session metadata
      const { data: userData } = await supabase.auth.getUser();
      if (userData?.user && userData.user.id === userId) {
        const metadata = userData.user.user_metadata || {};
        return {
          id: userId,
          username: metadata.username || userData.user.email?.split('@')[0] || 'User',
          email: userData.user.email || null,
          full_name: metadata.full_name || null,
          avatar_url: metadata.avatar_url || null,
          created_at: userData.user.created_at || new Date().toISOString(),
          updated_at: new Date().toISOString(),
        };
      }
      return null;
    }

    return data as Profile;
  } catch {
    return null;
  }
}

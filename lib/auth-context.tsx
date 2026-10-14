"use client";

import React, { createContext, useContext, useEffect, useState, useTransition } from 'react';
import type { User, Session } from '@supabase/supabase-js';
import { getSupabaseBrowserClient } from './client';
import { Profile } from './auth-types';
import {
  fetchUserProfile,
  loginWithIdentifier,
  registerUser,
  logoutUser,
} from './auth';

interface AuthContextType {
  user: User | null;
  profile: Profile | null;
  session: Session | null;
  loading: boolean;
  signIn: (identifier: string, pass: string) => Promise<{ success: boolean; error?: string }>;
  signUp: (params: {
    username: string;
    email: string;
    password: string;
    fullName?: string;
  }) => Promise<{ success: boolean; error?: string; requiresEmailConfirmation?: boolean }>;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  continueAsGuest: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const GUEST_STORAGE_KEY = 'medlearn_guest_session';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [, startTransition] = useTransition();

  const loadUserProfile = async (currentUser: User) => {
    try {
      const p = await fetchUserProfile(currentUser.id);
      startTransition(() => {
        setProfile(p);
      });
    } catch {
      // ignore
    }
  };

  const applyGuestSession = () => {
    const guestUser = {
      id: "guest-scholar-id",
      email: "guest@medlearn.local",
      user_metadata: {
        username: "guest_scholar",
        full_name: "Guest Medical Scholar",
      },
      app_metadata: { provider: "guest" },
      aud: "authenticated",
      created_at: new Date().toISOString(),
    } as unknown as User;

    setUser(guestUser);
    setProfile({
      id: "guest-scholar-id",
      username: "guest_scholar",
      full_name: "Guest Medical Scholar",
      email: "guest@medlearn.local",
      avatar_url: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    });
    setLoading(false);
  };

  const continueAsGuest = () => {
    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(GUEST_STORAGE_KEY, 'true');
      } catch {
        // ignore storage errors
      }
    }
    applyGuestSession();
  };

  useEffect(() => {
    let isMounted = true;
    const safetyTimer = setTimeout(() => {
      if (isMounted) {
        setLoading(false);
      }
    }, 2000);

    // Check if user previously chosen guest demo
    if (typeof window !== 'undefined') {
      try {
        if (localStorage.getItem(GUEST_STORAGE_KEY) === 'true') {
          applyGuestSession();
          clearTimeout(safetyTimer);
          return;
        }
      } catch {
        // ignore
      }
    }

    const supabase = getSupabaseBrowserClient();

    // 1. Check existing session on mount
    supabase.auth
      .getSession()
      .then(({ data: { session: initialSession } }) => {
        if (!isMounted) return;
        setSession(initialSession);
        setUser(initialSession?.user ?? null);
        if (initialSession?.user) {
          loadUserProfile(initialSession.user);
        }
        setLoading(false);
        clearTimeout(safetyTimer);
      })
      .catch(() => {
        if (isMounted) {
          setLoading(false);
          clearTimeout(safetyTimer);
        }
      });

    // 2. Subscribe to auth state changes (login, logout, token refresh)
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, newSession) => {
      if (!isMounted) return;
      setSession(newSession);
      setUser(newSession?.user ?? null);

      if (newSession?.user) {
        await loadUserProfile(newSession.user);
      } else {
        setProfile(null);
      }
      setLoading(false);
      clearTimeout(safetyTimer);
    });

    return () => {
      isMounted = false;
      clearTimeout(safetyTimer);
      subscription.unsubscribe();
    };
  }, []);

  const handleSignIn = async (identifier: string, pass: string) => {
    const result = await loginWithIdentifier({ identifier, password: pass });
    if (result.success) {
      if (typeof window !== 'undefined') {
        try {
          localStorage.removeItem(GUEST_STORAGE_KEY);
        } catch {}
      }
      const supabase = getSupabaseBrowserClient();
      const { data } = await supabase.auth.getUser();
      if (data?.user) {
        setUser(data.user);
        await loadUserProfile(data.user);
      }
    }
    return result;
  };

  const handleSignUp = async (params: {
    username: string;
    email: string;
    password: string;
    fullName?: string;
  }) => {
    if (typeof window !== 'undefined') {
      try {
        localStorage.removeItem(GUEST_STORAGE_KEY);
      } catch {}
    }
    return await registerUser(params);
  };

  const handleSignOut = async () => {
    if (typeof window !== 'undefined') {
      try {
        localStorage.removeItem(GUEST_STORAGE_KEY);
      } catch {}
    }
    await logoutUser();
    setUser(null);
    setSession(null);
    setProfile(null);
  };

  const refreshProfile = async () => {
    if (user) {
      await loadUserProfile(user);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        profile,
        session,
        loading,
        signIn: handleSignIn,
        signUp: handleSignUp,
        signOut: handleSignOut,
        refreshProfile,
        continueAsGuest,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

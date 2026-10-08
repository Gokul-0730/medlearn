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
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

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

  useEffect(() => {
    const supabase = getSupabaseBrowserClient();

    // 1. Check existing session on mount
    supabase.auth.getSession().then(({ data: { session: initialSession } }) => {
      setSession(initialSession);
      setUser(initialSession?.user ?? null);
      if (initialSession?.user) {
        loadUserProfile(initialSession.user);
      }
      setLoading(false);
    });

    // 2. Subscribe to auth state changes (login, logout, token refresh)
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, newSession) => {
      setSession(newSession);
      setUser(newSession?.user ?? null);

      if (newSession?.user) {
        await loadUserProfile(newSession.user);
      } else {
        setProfile(null);
      }
      setLoading(false);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const handleSignIn = async (identifier: string, pass: string) => {
    const result = await loginWithIdentifier({ identifier, password: pass });
    if (result.success) {
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
    return await registerUser(params);
  };

  const handleSignOut = async () => {
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

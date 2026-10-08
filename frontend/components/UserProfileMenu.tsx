"use client";

import React, { useState, useRef, useEffect } from "react";
import { useAuth } from "../lib/auth-context";

export function UserProfileMenu() {
  const { user, profile, signOut } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close popup when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen]);

  if (!user) return null;

  const displayName =
    profile?.full_name || profile?.username || user.user_metadata?.full_name || user.email?.split("@")[0] || "User";
  const username = profile?.username || user.user_metadata?.username || user.email?.split("@")[0] || "user";
  const email = user.email || profile?.email || "No email";
  const initial = (displayName.charAt(0) || "U").toUpperCase();

  return (
    <div className="user-profile-menu-container" ref={menuRef}>
      <button
        type="button"
        className="profile-button"
        onClick={() => setIsOpen(!isOpen)}
        title="View profile & account settings"
        aria-expanded={isOpen}
      >
        <span className="profile-avatar">{initial}</span>
        <span className="profile-name">{displayName}</span>
        <span style={{ fontSize: "10px", opacity: 0.6 }}>▼</span>
      </button>

      {isOpen && (
        <div className="profile-dropdown-glass">
          <div className="profile-dropdown-header">
            <div className="profile-dropdown-avatar">{initial}</div>
            <div className="profile-dropdown-info">
              <strong className="profile-dropdown-name">{displayName}</strong>
              <span className="profile-dropdown-username">@{username}</span>
              <span className="profile-dropdown-email">{email}</span>
            </div>
          </div>

          <div className="profile-dropdown-divider" />

          <div className="profile-dropdown-meta">
            <div className="profile-meta-row">
              <span className="meta-label">Role:</span>
              <span className="meta-value">Medical Scholar</span>
            </div>
            <div className="profile-meta-row">
              <span className="meta-label">Auth Provider:</span>
              <span className="meta-value">Supabase Auth</span>
            </div>
            <div className="profile-meta-row">
              <span className="meta-label">Security:</span>
              <span className="meta-value text-emerald">Active RLS Gated</span>
            </div>
          </div>

          <div className="profile-dropdown-divider" />

          <button
            type="button"
            className="profile-logout-btn"
            onClick={async () => {
              setIsOpen(false);
              await signOut();
            }}
          >
            <span>🚪</span>
            <span>Sign Out</span>
          </button>
        </div>
      )}
    </div>
  );
}

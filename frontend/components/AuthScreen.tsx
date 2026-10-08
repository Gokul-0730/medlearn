"use client";

import React, { useState, useTransition } from "react";
import { useAuth } from "../lib/auth-context";
import {
  validateUsername,
  validateEmail,
  evaluatePasswordStrength,
  checkUsernameAvailability,
} from "../lib/auth";

export function AuthScreen() {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");

  // Form states
  const [identifier, setIdentifier] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  // UI states
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [usernameChecking, setUsernameChecking] = useState(false);
  const [usernameAvailable, setUsernameAvailable] = useState<boolean | null>(null);
  const [isPending, startTransition] = useTransition();

  const strength = evaluatePasswordStrength(password);

  // Handle live username check on blur
  const handleUsernameBlur = async () => {
    if (!username.trim()) {
      setUsernameAvailable(null);
      return;
    }
    const val = validateUsername(username);
    if (!val.isValid) {
      setUsernameAvailable(false);
      return;
    }
    setUsernameChecking(true);
    const res = await checkUsernameAvailability(username);
    setUsernameChecking(false);
    setUsernameAvailable(res.available);
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    if (!identifier.trim()) {
      setErrorMsg("Please enter your username or email.");
      return;
    }
    if (!password) {
      setErrorMsg("Please enter your password.");
      return;
    }

    startTransition(async () => {
      const res = await signIn(identifier, password);
      if (!res.success) {
        setErrorMsg(res.error || "Login failed. Please check your credentials.");
      }
    });
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    const userVal = validateUsername(username);
    if (!userVal.isValid) {
      setErrorMsg(userVal.error || "Invalid username.");
      return;
    }

    const emailVal = validateEmail(email);
    if (!emailVal.isValid) {
      setErrorMsg(emailVal.error || "Invalid email.");
      return;
    }

    if (password.length < 8) {
      setErrorMsg("Password must be at least 8 characters.");
      return;
    }

    if (password !== confirmPassword) {
      setErrorMsg("Passwords do not match.");
      return;
    }

    startTransition(async () => {
      const res = await signUp({
        username,
        email,
        password,
        fullName: fullName || undefined,
      });

      if (!res.success) {
        setErrorMsg(res.error || "Registration failed.");
      } else {
        if (res.requiresEmailConfirmation) {
          setSuccessMsg(
            "Account created! Please check your email to confirm your account before logging in."
          );
          setMode("login");
        } else {
          setSuccessMsg("Account successfully created! You are now logged in.");
        }
      }
    });
  };

  return (
    <div className="auth-container">
      <div className="auth-glass-card">
        {/* Brand Header */}
        <div className="auth-brand-header">
          <div className="auth-brand-icon">🩺</div>
          <h1 className="auth-brand-title">MedLearn</h1>
          <p className="auth-brand-subtitle">
            Secure Medical Clinical Learning & Emergency Hub
          </p>
        </div>

        {/* Tab Switcher */}
        <div className="auth-tab-bar">
          <button
            type="button"
            className={`auth-tab-btn ${mode === "login" ? "active" : ""}`}
            onClick={() => {
              setMode("login");
              setErrorMsg(null);
            }}
          >
            Sign In
          </button>
          <button
            type="button"
            className={`auth-tab-btn ${mode === "register" ? "active" : ""}`}
            onClick={() => {
              setMode("register");
              setErrorMsg(null);
            }}
          >
            Create Account
          </button>
        </div>

        {/* Status Alerts */}
        {errorMsg && (
          <div className="auth-alert error">
            <span className="auth-alert-icon">⚠️</span>
            <span>{errorMsg}</span>
          </div>
        )}
        {successMsg && (
          <div className="auth-alert success">
            <span className="auth-alert-icon">✓</span>
            <span>{successMsg}</span>
          </div>
        )}

        {/* LOGIN FORM */}
        {mode === "login" && (
          <form onSubmit={handleLoginSubmit} className="auth-form">
            <div className="auth-field">
              <label className="auth-label">Username or Email</label>
              <input
                type="text"
                className="auth-input"
                placeholder="doctor_sarah or sarah@hospital.org"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                autoComplete="username"
                required
              />
            </div>

            <div className="auth-field">
              <div className="auth-label-row">
                <label className="auth-label">Password</label>
                <button
                  type="button"
                  className="auth-show-pass-btn"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
              <input
                type={showPassword ? "text" : "password"}
                className="auth-input"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>

            <button
              type="submit"
              className="auth-submit-btn"
              disabled={isPending}
            >
              {isPending ? "Authenticating..." : "Sign In to MedLearn"}
            </button>

            <div className="auth-footer-prompt">
              Don&apos;t have an account?{" "}
              <button
                type="button"
                className="auth-link-btn"
                onClick={() => {
                  setMode("register");
                  setErrorMsg(null);
                }}
              >
                Create one now
              </button>
            </div>
          </form>
        )}

        {/* REGISTER FORM */}
        {mode === "register" && (
          <form onSubmit={handleRegisterSubmit} className="auth-form">
            <div className="auth-field">
              <div className="auth-label-row">
                <label className="auth-label">Username</label>
                {usernameChecking && (
                  <span className="auth-hint checking">Checking availability...</span>
                )}
                {!usernameChecking && usernameAvailable === true && (
                  <span className="auth-hint available">✓ Available</span>
                )}
                {!usernameChecking && usernameAvailable === false && (
                  <span className="auth-hint taken">✗ Taken or invalid</span>
                )}
              </div>
              <input
                type="text"
                className="auth-input"
                placeholder="e.g. med_student99"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setUsernameAvailable(null);
                }}
                onBlur={handleUsernameBlur}
                autoComplete="username"
                required
              />
              <span className="auth-sub-hint">
                3–30 characters: letters, numbers, and underscores only.
              </span>
            </div>

            <div className="auth-field">
              <label className="auth-label">Email Address</label>
              <input
                type="email"
                className="auth-input"
                placeholder="student@medical.edu"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </div>

            <div className="auth-field">
              <label className="auth-label">Full Name (Optional)</label>
              <input
                type="text"
                className="auth-input"
                placeholder="e.g. Dr. Alex Morgan"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                autoComplete="name"
              />
            </div>

            <div className="auth-field">
              <div className="auth-label-row">
                <label className="auth-label">Password</label>
                <button
                  type="button"
                  className="auth-show-pass-btn"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
              <input
                type={showPassword ? "text" : "password"}
                className="auth-input"
                placeholder="Minimum 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />

              {/* Password Strength Meter */}
              {password.length > 0 && (
                <div className="auth-strength-container">
                  <div className="auth-strength-bar-bg">
                    <div
                      className="auth-strength-bar-fill"
                      style={{
                        width: `${Math.max(15, (strength.score / 4) * 100)}%`,
                        backgroundColor: strength.color,
                      }}
                    />
                  </div>
                  <div className="auth-strength-meta">
                    <span>Strength: </span>
                    <strong style={{ color: strength.color }}>{strength.label}</strong>
                  </div>
                  <div className="auth-strength-checks">
                    <span className={strength.hasMinLength ? "pass" : "fail"}>
                      {strength.hasMinLength ? "✓" : "•"} 8+ chars
                    </span>
                    <span className={strength.hasUppercase && strength.hasLowercase ? "pass" : "fail"}>
                      {strength.hasUppercase && strength.hasLowercase ? "✓" : "•"} Aa (cases)
                    </span>
                    <span className={strength.hasNumber ? "pass" : "fail"}>
                      {strength.hasNumber ? "✓" : "•"} 0-9
                    </span>
                    <span className={strength.hasSpecial ? "pass" : "fail"}>
                      {strength.hasSpecial ? "✓" : "•"} Symbol (!@#)
                    </span>
                  </div>
                </div>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-label">Confirm Password</label>
              <input
                type={showPassword ? "text" : "password"}
                className="auth-input"
                placeholder="Re-type password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              {confirmPassword.length > 0 && (
                <span
                  className={`auth-sub-hint ${
                    password === confirmPassword ? "text-emerald" : "text-amber"
                  }`}
                >
                  {password === confirmPassword
                    ? "✓ Passwords match"
                    : "✗ Passwords do not match"}
                </span>
              )}
            </div>

            <button
              type="submit"
              className="auth-submit-btn"
              disabled={isPending}
            >
              {isPending ? "Creating Account..." : "Complete Registration"}
            </button>

            <div className="auth-footer-prompt">
              Already have an account?{" "}
              <button
                type="button"
                className="auth-link-btn"
                onClick={() => {
                  setMode("login");
                  setErrorMsg(null);
                }}
              >
                Sign In
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

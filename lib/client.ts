import { createBrowserClient } from '@supabase/ssr'

export function createClient() {
  const url =
    process.env.NEXT_PUBLIC_SUPABASE_URL ||
    'https://iggagkzlvqovfntqbyil.supabase.co'
  const key =
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ||
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ||
    'sb_publishable_zu7N73ni_rI11Yi3U9E2fA_w_XwoWJO'

  return createBrowserClient(url, key)
}

// Singleton browser client instance for fast re-use
let browserClient: ReturnType<typeof createClient> | null = null

export function getSupabaseBrowserClient() {
  if (typeof window === 'undefined') {
    return createClient()
  }
  if (!browserClient) {
    browserClient = createClient()
  }
  return browserClient
}

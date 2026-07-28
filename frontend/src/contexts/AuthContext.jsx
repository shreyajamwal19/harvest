import { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react'
import api, { setUnauthorizedHandler } from '../services/api'

const AuthContext = createContext(null)

// eslint-disable-next-line react/prop-types -- `children` is a standard React prop; this project doesn't use PropTypes
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sessionMessage, setSessionMessage] = useState('')
  const logoutTimerRef = useRef(null)

  const clearLogoutTimer = useCallback(() => {
    if (logoutTimerRef.current) {
      clearTimeout(logoutTimerRef.current)
      logoutTimerRef.current = null
    }
  }, [])

  // Proactively logs the user out the moment their token actually expires,
  // rather than waiting for the next failed request to notice.
  const scheduleAutoLogout = useCallback((expiresAt) => {
    clearLogoutTimer()
    if (!expiresAt) return
    const msUntilExpiry = new Date(expiresAt).getTime() - Date.now()
    if (msUntilExpiry <= 0) return
    logoutTimerRef.current = setTimeout(() => {
      setUser(null)
      setSessionMessage('Your session has expired. Please log in again.')
    }, msUntilExpiry)
  }, [clearLogoutTimer])

  // Reactive fallback: if the server ever rejects a request with 401 (session
  // revoked, clock skew, expired between checks, etc.) clear auth state too.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearLogoutTimer()
      setUser((current) => (current ? null : current))
    })
    return () => setUnauthorizedHandler(null)
  }, [clearLogoutTimer])

  // Re-hydrate auth state from the httpOnly cookie whenever the app loads or
  // the page is refreshed. A 401 here just means "not logged in" - normal
  // for anonymous visitors, not an error condition.
  useEffect(() => {
    let cancelled = false
    api.get('/user/me')
      .then((response) => {
        if (cancelled) return
        setUser(response.data.user)
        scheduleAutoLogout(response.data.expiresAt)
      })
      .catch(() => {
        if (!cancelled) setUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [scheduleAutoLogout])

  const login = useCallback(async (email, password) => {
    const response = await api.post('/auth/login', { email, password })
    setSessionMessage('')
    setUser(response.data.user)
    scheduleAutoLogout(response.data.expiresAt)
    return response.data.user
  }, [scheduleAutoLogout])

  const signup = useCallback(async (name, email, password) => {
    const response = await api.post('/auth/signup', { name, email, password })
    setSessionMessage('')
    // Don't automatically log in after signup - user should be redirected to login page
    // setUser(response.data.user)
    // scheduleAutoLogout(response.data.expiresAt)
    return response.data.user
  }, [scheduleAutoLogout])

  const logout = useCallback(async () => {
    clearLogoutTimer()
    setUser(null)
    setSessionMessage('')
    try {
      await api.post('/auth/logout')
    } catch {
      // Client-side state is already cleared; the cookie expires naturally
      // even if this call fails (e.g. offline), so this is not user-facing.
    }
  }, [clearLogoutTimer])

  const clearSessionMessage = useCallback(() => setSessionMessage(''), [])

  const value = {
    user,
    isAuthenticated: !!user,
    loading,
    sessionMessage,
    clearSessionMessage,
    login,
    signup,
    logout,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

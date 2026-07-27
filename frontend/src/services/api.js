import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // The JWT lives in an httpOnly cookie set by the backend - this makes the
  // browser attach/receive it automatically. It also means client-side JS
  // (including any XSS payload) can never read the token directly.
  withCredentials: true,
})

// A 401 from these endpoints is an expected, form-level outcome (wrong
// password, duplicate email, etc.) and is handled locally by the calling
// component - it should never trigger a global "session expired" reaction.
const SKIP_GLOBAL_401_HANDLING = ['/auth/login', '/auth/signup']

let unauthorizedHandler = null

/** Registered once by AuthContext so the interceptor can clear auth state. */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || ''
    const isAuthAttempt = SKIP_GLOBAL_401_HANDLING.some((path) => url.includes(path))
    if (error.response?.status === 401 && !isAuthAttempt && unauthorizedHandler) {
      unauthorizedHandler()
    }
    return Promise.reject(error)
  }
)

/**
 * Turns an Axios error into a specific, human-readable message so the UI
 * never has to fall back to a generic "Unexpected error" string. Always
 * prefers what the backend actually said (including field-level validation
 * messages) over a made-up fallback.
 */
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error.response) {
    // Request never reached the server (network down, CORS block, backend offline, etc.)
    return 'Unable to reach the server. Please check your connection and try again.'
  }

  const data = error.response.data
  if (data?.validationErrors && Object.keys(data.validationErrors).length > 0) {
    return Object.values(data.validationErrors).join(' ')
  }
  if (data?.message) {
    return data.message
  }
  return fallback
}

export default api

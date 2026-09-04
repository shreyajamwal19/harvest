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

/**
 * Sends one turn to the Chef Brain. Pass the sessionId returned by a
 * previous call to continue that conversation, or omit it to start a new one.
 */
export function chefChat({ sessionId, message }) {
  return api.post('/chef/chat', { sessionId, message })
}

/**
 * Pantry API. Backed by the same PantryItem table the Chef Brain reads/writes via
 * chat commands ("I bought eggs", "remove onions", ...) - changes made here or in
 * chat show up in both places instantly.
 */
export function getPantryItems() {
  return api.get('/pantry')
}

export function addPantryItem({ ingredientName, quantity, unit, expiryDate }) {
  return api.post('/pantry', { ingredientName, quantity, unit, expiryDate })
}

export function removePantryItem(itemId) {
  return api.delete(`/pantry/${itemId}`)
}

export function updatePantryItemQuantity(itemId, quantity) {
  return api.patch(`/pantry/${itemId}`, { quantity })
}

/** expiryDate is a 'yyyy-MM-dd' string, or null to stop tracking expiry for this item. */
export function updatePantryItemExpiry(itemId, expiryDate) {
  return api.patch(`/pantry/${itemId}/expiry`, { expiryDate })
}

export function clearPantry() {
  return api.delete('/pantry')
}

/**
 * Saved Recipes API. Backed by the SAVED history event that was already modeled but never
 * had a caller - saving/unsaving here also logs that event for future personalization use.
 */
export function getSavedRecipes() {
  return api.get('/saved-recipes')
}

export function saveRecipe(recipe) {
  return api.post('/saved-recipes', recipe)
}

export function unsaveRecipe(savedRecipeId) {
  return api.delete(`/saved-recipes/${savedRecipeId}`)
}

/** Records a COOKED history event - called when Cooking Mode is completed. */
export function markRecipeCooked(title) {
  return api.post('/recipes/cooked', { title })
}

/** Reverse-chronological list of everything actually cooked (not just saved) - the Cooking History page. */
export function getCookingHistory() {
  return api.get('/recipes/cooked')
}

/** Public, unauthenticated - one real dish (with photo). Optional `query` picks the cuisine/dish. */
export function getShowcaseRecipe(query) {
  return api.get('/public/showcase', { params: query ? { query } : {} })
}

/**
 * Deterministic single-day swap for the Meal Plan page - no chat/LLM round trip, since picking
 * a replacement day is exactly the structured, deterministic work MealPlanningService already
 * does. `excludeTitles` should be every title currently in the plan.
 */
export function regenerateMealPlanDay({ excludeTitles, mealType }) {
  return api.post('/meal-plan/regenerate-day', { excludeTitles, mealType })
}

/** Change password while logged in - PUT since it updates the existing user resource in place. */
export function changePassword({ currentPassword, newPassword }) {
  return api.put('/user/password', { currentPassword, newPassword })
}

/** Irreversible - requires currentPassword confirmation, verified server-side. */
export function deleteAccount(currentPassword) {
  return api.delete('/user', { data: { currentPassword } })
}

/** GDPR-style data export - everything Harvest stores about this account, bundled as JSON. */
export function exportUserData() {
  return api.get('/user/export')
}

/**
 * Learned preferences (favorite cuisines, dietary restrictions, etc.) - previously viewable/
 * manageable only via chat commands ("show my preferences", "forget X"). Same underlying table,
 * so a change made here or in chat shows up in both.
 */
export function getPreferences() {
  return api.get('/preferences')
}

export function deletePreference(preferenceId) {
  return api.delete(`/preferences/${preferenceId}`)
}

/** Same as the "reset my profile" chat command - clears every learned preference. */
export function resetPreferences() {
  return api.delete('/preferences')
}

export default api

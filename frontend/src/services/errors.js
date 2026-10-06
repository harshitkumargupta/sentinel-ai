// Turns any Axios/API error into a user-friendly message, preferring the API error code/message.
export function messageFromError(err, fallback = 'Something went wrong. Please try again.') {
  const apiError = err?.response?.data?.error;
  if (apiError?.message) {
    return apiError.code ? `${apiError.message} (${apiError.code})` : apiError.message;
  }
  if (err?.message) {
    return err.message;
  }
  return fallback;
}

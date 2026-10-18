export function explainIdentityError(error, requiredIdentity, fallback) {
    if (error?.message === "Authentication required") {
        return `A signed-in ${requiredIdentity} account is required for this information or action. Selecting a role and hospital does not establish an account identity.`
    }
    if (error?.message === "Access denied") {
        return `The signed-in account does not have the required role or hospital assignment for this ${requiredIdentity} action.`
    }
    return error?.message || fallback
}

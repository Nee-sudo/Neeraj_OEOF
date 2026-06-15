# Find Friends Fix & Resolution Outline

This document details the minimal, targeted surgical updates required to restore full functionality to the **Find Friends** system.

---

## Minimal Surgical Fix Plan

### File 1: `app/src/main/java/com/example/ui/AppViewModel.kt`
* **Area:** `startBackendHealthChecking()`
* **Issue:** Expired cloud developer quick tunnels block the background sync loop for fetching backend profiles.
* **Minimal Fix:** Add a fallback connection state machine inside the health checker to attempt `http://10.0.2.2:4000/` (the local AI Studio VM loopback) if the configured cloud tunnel remains unresponsive.

---

## Exact Bug Locations

### 1) Backend Endpoint Connection Failure
* **File:** `/app/src/main/java/com/example/ui/AppViewModel.kt`
* **Context:** `startBackendHealthChecking()` (Lines 479-502)
* **Code Modification:** Add fallback toggling to `10.0.2.2:4000` on consecutive network timeouts/exceptions.

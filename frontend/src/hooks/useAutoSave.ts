import { useState, useEffect, useRef, useCallback } from 'react';

interface UseAutoSaveResult {
  /** Whether a save operation is currently in progress */
  isSaving: boolean;
  /** Timestamp of the last successful save, or null if never saved */
  lastSaved: Date | null;
  /** Whether the current value differs from the last saved value */
  dirty: boolean;
  /** Immediately flush any pending save and save current value */
  forceSave: () => void;
}

/**
 * Debounced auto-save hook.
 * When `value` changes, waits `delay` ms of inactivity before calling `saveFn`.
 * On component unmount, flushes any pending unsaved content.
 *
 * @param value - The string value to watch for changes
 * @param saveFn - Async function to persist the value
 * @param delay - Debounce delay in ms (default 2000)
 */
export function useAutoSave(
  value: string,
  saveFn: (val: string) => Promise<void>,
  delay: number = 2000,
): UseAutoSaveResult {
  const [isSaving, setIsSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [dirty, setDirty] = useState(false);

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const valueRef = useRef(value);
  const savedValueRef = useRef(value);
  const isMountedRef = useRef(true);
  const saveFnRef = useRef(saveFn);
  const saveGenRef = useRef(0);

  // Keep saveFn ref up to date without triggering effect re-runs
  saveFnRef.current = saveFn;

  // Keep latest value in ref for flush on unmount
  valueRef.current = value;

  const performSave = useCallback(async (val: string) => {
    if (!isMountedRef.current) return;
    const gen = ++saveGenRef.current;
    setIsSaving(true);
    try {
      await saveFnRef.current(val);
      if (gen !== saveGenRef.current) return; // stale save, skip
      if (isMountedRef.current) {
        setLastSaved(new Date());
        savedValueRef.current = val;
        setDirty(false);
      }
    } catch {
      // Save failed — keep dirty flag so retry is possible
    } finally {
      if (isMountedRef.current) {
        setIsSaving(false);
      }
    }
  }, []);

  const forceSave = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    performSave(valueRef.current);
  }, [performSave]);

  // Debounced auto-save effect
  useEffect(() => {
    isMountedRef.current = true;

    if (value !== savedValueRef.current) {
      setDirty(true);

      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }

      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        performSave(value);
      }, delay);
    }

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [value, delay, performSave]);

  // Flush unsaved content on unmount
  useEffect(() => {
    return () => {
      isMountedRef.current = false;
      // Flush pending save if dirty and no save in progress
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      if (valueRef.current !== savedValueRef.current) {
        // Fire-and-forget — cannot set state on unmounted component
        saveFnRef.current(valueRef.current).catch(() => {
          // ignore errors on unmount flush
        });
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { isSaving, lastSaved, dirty, forceSave };
}

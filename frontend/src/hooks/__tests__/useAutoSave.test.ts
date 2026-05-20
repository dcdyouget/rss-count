import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAutoSave } from '../useAutoSave';

describe('useAutoSave', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  // T8-1: content changes → wait 2s → triggers save function
  it('T8-1: should trigger saveFn after delay when value changes', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined);
    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: '' } },
    );

    // Change value
    rerender({ value: 'Hello World' });

    // Should not have called saveFn immediately
    expect(saveFn).not.toHaveBeenCalled();
    expect(result.current.dirty).toBe(true);

    // Advance timers and flush pending async work
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(saveFn).toHaveBeenCalledWith('Hello World');
    expect(saveFn).toHaveBeenCalledTimes(1);
  });

  // T8-2: change within delay → reset timer, only 1 request
  it('T8-2: should debounce multiple rapid changes into a single save', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: 'A' } },
    );

    // Rapidly change value multiple times within delay window
    vi.advanceTimersByTime(500);
    rerender({ value: 'AB' });

    vi.advanceTimersByTime(500);
    rerender({ value: 'ABC' });

    vi.advanceTimersByTime(500);
    rerender({ value: 'ABCD' });

    // Not called yet (only 1500ms since first change, and each
    // subsequent change resets the timer)
    expect(saveFn).not.toHaveBeenCalled();

    // Advance to complete the delay after last change
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(saveFn).toHaveBeenCalledTimes(1);
    expect(saveFn).toHaveBeenCalledWith('ABCD');
  });

  // T8-3: save in progress → isSaving = true
  it('T8-3: should set isSaving=true while save is in progress', async () => {
    let resolveSave: () => void;
    const saveFn = vi.fn().mockImplementation(() => {
      return new Promise<void>((resolve) => {
        resolveSave = resolve;
      });
    });

    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: '' } },
    );

    rerender({ value: 'Test' });

    // Flush the timer to trigger save
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    // The saveFn has been called but hasn't resolved yet
    expect(saveFn).toHaveBeenCalled();
    expect(result.current.isSaving).toBe(true);

    // Resolve the save
    await act(async () => {
      resolveSave!();
    });

    expect(result.current.isSaving).toBe(false);
  });

  // T8-4: save succeeds → lastSaved updated
  it('T8-4: should update lastSaved after successful save', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined);
    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: '' } },
    );

    expect(result.current.lastSaved).toBeNull();

    rerender({ value: 'Saved Content' });

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(result.current.lastSaved).toBeInstanceOf(Date);
    expect(result.current.dirty).toBe(false);
  });

  // forceSave triggers immediate save
  it('should immediately save when forceSave is called', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined);
    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: 'Initial' } },
    );

    rerender({ value: 'Changed' });

    // Call forceSave before debounce timer fires
    await act(async () => {
      result.current.forceSave();
    });

    // Should have called saveFn immediately without waiting
    expect(saveFn).toHaveBeenCalledWith('Changed');
  });

  // Verify dirty flag behavior
  it('should set dirty=true when value changes from saved state', async () => {
    const saveFn = vi.fn().mockResolvedValue(undefined);
    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: '' } },
    );

    // First change - trigger save
    rerender({ value: 'Original' });

    // Wait for debounce and save
    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(saveFn).toHaveBeenCalledWith('Original');
    expect(result.current.dirty).toBe(false);

    // Change value again
    rerender({ value: 'Modified' });
    expect(result.current.dirty).toBe(true);
  });

  // Verify save failure keeps dirty flag
  it('should keep dirty=true if save fails', async () => {
    const saveFn = vi.fn().mockRejectedValue(new Error('Save failed'));
    const { result, rerender } = renderHook(
      ({ value }) => useAutoSave(value, saveFn, 2000),
      { initialProps: { value: '' } },
    );

    rerender({ value: 'Failing content' });

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    // Save was attempted
    expect(saveFn).toHaveBeenCalled();
    // Still dirty because save failed
    expect(result.current.dirty).toBe(true);
    expect(result.current.isSaving).toBe(false);
  });
});

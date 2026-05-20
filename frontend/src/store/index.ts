import { create } from 'zustand';

// ---------- 稿件编辑器状态 ----------

interface DraftState {
  isDirty: boolean;
  activeTab: 'richtext' | 'plaintext';
  setDirty: (dirty: boolean) => void;
  setActiveTab: (tab: 'richtext' | 'plaintext') => void;
}

export const useDraftStore = create<DraftState>((set) => ({
  isDirty: false,
  activeTab: 'richtext',
  setDirty: (dirty) => set({ isDirty: dirty }),
  setActiveTab: (tab) => set({ activeTab: tab }),
}));

// ---------- UI 状态 ----------

interface UIState {
  taskDrawerOpen: boolean;
  selectedTaskId: number | null;
  openTaskDrawer: (id: number) => void;
  closeTaskDrawer: () => void;
}

export const useUIStore = create<UIState>((set) => ({
  taskDrawerOpen: false,
  selectedTaskId: null,
  openTaskDrawer: (id) => set({ taskDrawerOpen: true, selectedTaskId: id }),
  closeTaskDrawer: () => set({ taskDrawerOpen: false, selectedTaskId: null }),
}));

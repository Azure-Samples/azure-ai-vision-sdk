import {createSlice} from '@reduxjs/toolkit';

const initialState = {
  endpoint: '',
  apiKey: '',
  isActivePassive: 'PassiveActive',
  isImageInClient: false,
  lastSessionId: '',
};

const globalSlice = createSlice({
  name: 'global',
  initialState,
  reducers: {
    setEndpoint: (state, action) => {
      state.endpoint = action.payload;
    },
    setApiKey: (state, action) => {
      state.apiKey = action.payload;
    },
    setActivePassive: (state, action) => {
      state.isActivePassive = action.payload;
    },
    setImageInClient: (state, action) => {
      state.isImageInClient = action.payload;
    },
    setLastSessionId: (state, action) => {
      state.lastSessionId = action.payload;
    },
  },
});

export const {setEndpoint, setApiKey, setActivePassive, setImageInClient, setLastSessionId} = globalSlice.actions;
export default globalSlice.reducer;
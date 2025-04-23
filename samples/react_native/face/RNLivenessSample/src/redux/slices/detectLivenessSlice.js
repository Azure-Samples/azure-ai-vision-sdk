import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import axios from 'axios';

export const detectLiveness = createAsyncThunk(
  'azure/detectLiveness',
  async ({ baseUrl, key, body }, thunkAPI) => {
    try {
      const response = await axios.post(
        `${baseUrl}face/v1.2/detectLiveness-sessions`,
        body,
        { headers: {
          'Ocp-Apim-Subscription-Key': `${key}`,
          'Content-Type': 'application/json; charset=UTF-8',
        } }
      );

      return thunkAPI.fulfillWithValue(response);
    } catch (error) {
      return thunkAPI.rejectWithValue(error.response?.data || error.message);
    }
  }
);

const detectLivenessSlice = createSlice({
  name: 'detectLiveness',
  initialState: {
    data: null,
    loading: false,
    error: null,
    sessionId: null,
    authToken: null,
  },
  extraReducers: builder => {
    builder
      .addCase(detectLiveness.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(detectLiveness.fulfilled, (state, action) => {
        state.loading = false;
        state.data = action.payload;
        state.authToken = action.payload.data.authToken;
        state.sessionId = action.payload.data.sessionId;
        state.error = null;
      })
      .addCase(detectLiveness.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload || action.error.message;
        state.data = null;
        state.authToken = null;
        state.sessionId = null;
      });
  },
});

export default detectLivenessSlice.reducer;

import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import axios from 'axios';

export const getLivenessResult = createAsyncThunk(
  'azure/getLivenessResult',
  async ({ baseUrl, key, sessionId }, thunkAPI) => {
    try {
      const response = await axios.get(
        `${baseUrl}/detectLiveness-sessions/${sessionId}`,
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
  name: 'getLivenessResult',
  initialState: {
    data: null,
    loading: false,
    error: null,
  },
  extraReducers: builder => {
    builder
      .addCase(getLivenessResult.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(getLivenessResult.fulfilled, (state, action) => {
        state.loading = false;
        state.data = action.payload;
      })
      .addCase(getLivenessResult.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload || action.error.message;
      });
  },
});

export default detectLivenessSlice.reducer;

import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import axios from 'axios';

export const detectLivenessWithVerification = createAsyncThunk(
  'azure/detectLivenessWithVerification',
  async ({ baseUrl, key, body }, thunkAPI) => {
    try {
      const response = await axios.post(
        `${baseUrl}/face/v1.2/detectLivenessWithVerify-sessions`,
        body,
        { headers: {
          'Ocp-Apim-Subscription-Key': `${key}`,
          'Content-Type': 'multipart/form-data',
        } }
      );
      thunkAPI.fulfillWithValue(response);
      return response;
    } catch (error) {
      return thunkAPI.rejectWithValue(error.response?.data || error.message);
    }
  }
);

const detectLivenessWithVerificationSlice = createSlice({
  name: 'detectLivenessWithVerification',
  initialState: {
    data: null,
    loading: false,
    error: null,
  },
  extraReducers: builder => {
    builder
      .addCase(detectLivenessWithVerification.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(detectLivenessWithVerification.fulfilled, (state, action) => {
        state.loading = false;
        state.data = action.payload;
      })
      .addCase(detectLivenessWithVerification.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload || action.error.message;
      });
  },
});

export default detectLivenessWithVerificationSlice.reducer;
import {createSlice, createAsyncThunk} from '@reduxjs/toolkit';
import axios from 'axios';

export const getLivenessWithVerifySession = createAsyncThunk(
  'azure/getLivenessWithVerifySession',
  async ({baseUrl, key, sessionId}, thunkAPI) => {
    try {
      const response = await axios.get(
        `${baseUrl}face/v1.2/detectLivenessWithVerify-sessions/${sessionId}`,
        {
          headers: {
            'Ocp-Apim-Subscription-Key': `${key}`,
            'Content-Type': 'application/json; charset=UTF-8',
          },
        },
      );
      return thunkAPI.fulfillWithValue(response);
    } catch (error) {
      return thunkAPI.rejectWithValue(error.response?.data || error.message);
    }
  },
);

const getLivenessWithVerificationResult = createSlice({
  name: 'getLivenessWithVerifySession',
  initialState: {
    data: null,
    loading: false,
    error: null,
  },
  extraReducers: builder => {
    builder
      .addCase(getLivenessWithVerifySession.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(getLivenessWithVerifySession.fulfilled, (state, action) => {
        state.loading = false;s
        state.data = action.payload;
      })
      .addCase(getLivenessWithVerifySession.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload || action.error.message;
      });
  },
});

export default getLivenessWithVerificationResult.reducer;

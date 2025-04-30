import {combineReducers, configureStore} from '@reduxjs/toolkit';
import detectionReducer from '../slices/detectLivenessSlice';
import detectionWithVerificationReducer from '../slices/detectLivenessWithVerificationSlice';
import globalReducer from '../slices/globalSlice';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {persistReducer, persistStore} from 'redux-persist';
import getLivenessResult from '../slices/getLivenessResult';
import getLivenessWithVerifySession from '../slices/getLivenessWithVerificationResult';

const facePersistConfig = {
  key: 'global',
  storage: AsyncStorage,
};

const rootReducer = combineReducers({
  global: persistReducer(facePersistConfig, globalReducer),
  detection: detectionReducer,
  detectionWithVerification: detectionWithVerificationReducer,
  getLivenessResult: getLivenessResult,
  getLivenessWithVerifySession: getLivenessWithVerifySession,
});

export const store = configureStore({
  reducer: rootReducer,
  middleware: getDefaultMiddleware =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

export const persistor = persistStore(store);

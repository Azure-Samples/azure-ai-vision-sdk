import React, {useEffect} from 'react';
import {View, Text, TouchableOpacity, StyleSheet} from 'react-native';
import {useDispatch, useSelector} from 'react-redux';
import {getLivenessResult} from '../redux/slices/getLivenessResult';
import {getLivenessWithVerifySession} from '../redux/slices/getLivenessWithVerificationResult';
import {CommonActions} from '@react-navigation/native';

const ResultScreen = ({navigation, route}) => {
  const dispatch = useDispatch();
  const isLiveNess = route.params?.isLiveNess;
  const result = route.params?.result;
  const endpoint = useSelector(state => state.global.endpoint);
  const apiKey = useSelector(state => state.global.apiKey);
  const sessionId = useSelector(state => state.detection.sessionId);
  const getLivenessData = useSelector(
    state => state.getLivenessResult?.data?.data,
  );
  const getLivenessVerifySessionResult = useSelector(
    state => state.getLivenessWithVerifySession?.data?.data,
  );
  const verificationAuth = useSelector(
    state => state.detectionWithVerification?.data?.data,
  );

  useEffect(() => {
    if (isLiveNess) {
      dispatch(
        getLivenessResult({
          baseUrl: endpoint,
          key: apiKey,
          sessionId,
        }),
      );
    } else {
      dispatch(
        getLivenessWithVerifySession({
          baseUrl: endpoint,
          key: apiKey,
          sessionId: verificationAuth?.sessionId,
        }),
      );
    }
  }, []);

  const handleRetry = () => {
    navigation.dispatch(
      CommonActions.reset({
        index: 1,
        routes: [
          {
            name: 'HomeScreen',
            params: {
              type: isLiveNess,
            },
          },
        ],
      }),
    );
  };

  const goToMain = () => {
    navigation.dispatch(
      CommonActions.reset({
        index: 1,
        routes: [
          {
            name: 'HomeScreen',
            params: {
              type: 'home',
            },
          },
        ],
      }),
    );
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Result</Text>

      <Text style={styles.label}>Liveness status:</Text>
      <Text style={styles.placeholder}>
        {result?.status === 'error'
          ? result?.livenessError
          : isLiveNess && getLivenessData?.results?.attempts?.length > 0
          ? getLivenessData?.results?.attempts[0]?.result?.livenessDecision
          : !isLiveNess &&
            getLivenessVerifySessionResult?.results?.attempts?.length > 0
          ? getLivenessVerifySessionResult?.results?.attempts[0]?.result
              ?.livenessDecision
          : ''}
      </Text>

      {result?.status === 'error' && (
        <>
          <Text style={styles.label}>Liveness failure reason:</Text>
          <Text style={styles.placeholder}>{result?.livenessError}</Text>
        </>
      )}

      {(result?.status === 'error' || !isLiveNess) && (
        <>
          <Text style={styles.label}>Verification status:</Text>
          <Text style={styles.placeholder}>
            {result?.status === 'error'
              ? result?.recognitionError
              : verificationAuth?.status}
          </Text>
        </>
      )}

      {result?.status !== 'error' && !isLiveNess && (
        <>
          <Text style={styles.label}>Verification confidence:</Text>
          <Text style={styles.placeholder}>
            {getLivenessVerifySessionResult?.results?.attempts?.length > 0
              ? getLivenessVerifySessionResult?.results?.attempts[0]?.result
                  ?.verifyResult?.matchConfidence
              : ''}
          </Text>
        </>
      )}

      <View style={styles.bottomView}>
        <TouchableOpacity style={styles.button} onPress={handleRetry}>
          <Text style={styles.buttonText}>RETRY WITH SAME TOKEN</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button} onPress={goToMain}>
          <Text style={styles.buttonText}>GO TO MAIN SCREEN</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  title: {
    marginVertical: 10,
    fontSize: 36,
    color: '#3D5AFE',
    fontWeight: 'bold',
  },
  label: {
    marginTop: 20,
    fontSize: 16,
    color: '#000',
    fontWeight: 'bold',
  },
  input: {
    height: 40,
    borderColor: 'gray',
    borderWidth: 1,
    paddingLeft: 10,
  },
  placeholder: {
    marginTop: 6,
    color: 'gray',
  },
  checkboxContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 10,
  },
  checkboxLabel: {
    marginLeft: 8,
    fontSize: 16,
  },
  button: {
    backgroundColor: '#3D5AFE',
    padding: 15,
    marginBottom: 12,
  },
  bottomView: {
    position: 'absolute',
    bottom: 24,
    width: '100%',
    alignItems: 'center',
    alignSelf: 'center',
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
});

export default ResultScreen;

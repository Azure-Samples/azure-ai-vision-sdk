import React, {useEffect, useRef, useState} from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  SafeAreaView,
  Platform,
  PermissionsAndroid,
  Alert,
  NativeModules,
  DeviceEventEmitter,
  NativeEventEmitter,
} from 'react-native';
import {PERMISSIONS, request} from 'react-native-permissions';
import {useDispatch, useSelector} from 'react-redux';
import {detectLiveness} from '../redux/slices/detectLivenessSlice';
import {detectLivenessWithVerification} from '../redux/slices/detectLivenessWithVerificationSlice';
import ImagePicker from 'react-native-image-crop-picker';

const {AzureLiveness, AzureLivenessManager} = NativeModules;
const emitter = new NativeEventEmitter(AzureLivenessManager);

const HomeScreen = ({navigation, route}) => {
  const dispatch = useDispatch();
  const [image, setImage] = useState('');

  const endpoint = useSelector(state => state.global.endpoint);
  const apiKey = useSelector(state => state.global.apiKey);
  const isImageInClient = useSelector(state => state.global.isImageInClient);
  const verificationAuth = useSelector(
    state => state.detectionWithVerification?.data?.data,
  );
  const authToken = useSelector(state => state.detection.authToken);
  const isActivePassive = useSelector(state => state.global.isActivePassive);

  const isActionTriggeredRef = useRef(false);
  let routeType = route?.params?.type || '';

  useEffect(() => {
    requestCameraPermission();
  }, []);

  useEffect(() => {
    const handler = data => {
      if (!isActionTriggeredRef.current) {
        isActionTriggeredRef.current = true;
        console.log('result----->', data);
        navigateToResultScreen(data);
      }
    };

    if (Platform.OS === 'android') {
      DeviceEventEmitter.addListener('getLivenessResultAndroid', handler);
    } else {
      emitter.addListener('LivenessResultEvent', handler);
    }

    return () => {
      if (Platform.OS === 'android') {
        DeviceEventEmitter.removeAllListeners('getLivenessResultAndroid');
      } else {
        emitter.removeAllListeners('LivenessResultEvent', handler);
      }
    };
  }, []);

  const handleSettings = () => {
    navigation.navigate('SettingsScreen');
  };

  const navigateToResultScreen = result => {
    navigation.navigate('ResultScreen', {
      isLiveNess:
        Platform.OS === 'android' ? result?.data : result?.isLiveNess,
      result,
    });
  };

  const requestCameraPermission = async () => {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
          {
            title: 'RNAzureLiveness Camera Permission',
            message:
              'RNAzureLiveness needs access to your camera ' +
              'to detect liveness with your face',
            buttonNeutral: 'Ask Me Later',
            buttonNegative: 'Cancel',
            buttonPositive: 'OK',
          },
        );
        if (granted === PermissionsAndroid.RESULTS.GRANTED) {
          // Permission granted
        } else {
          // Camera permission denied
        }
      } catch (err) {
        console.warn(err);
      }
    } else {
      request(PERMISSIONS.IOS.CAMERA).then(status => {
        // Check permission status
      });
    }
  };

  const startLiveness = async (sessionToken, liveness) => {
    if (Platform.OS === 'android') {
      try {
        AzureLiveness.startLiveness(
          sessionToken,
          isImageInClient && image?.length > 0
            ? image?.replace('file://', '')
            : '',
          liveness,
        );
      } catch (err) {
        console.error('Liveness error:', err);
      }
    } else {
      try {
        await AzureLivenessManager.startLivenessDetection(
          sessionToken,
          liveness,
        );
      } catch (error) {
        console.error('Liveness Error:', error);
      }
    }
  };

  useEffect(() => {
    if (route && routeType !== 'home') {
      if (route && route?.params?.isRetry) {
        if (routeType && authToken?.length > 0) {
          startLiveness(authToken, 'true');
        } else if (!routeType && verificationAuth?.authToken?.length > 0) {
          startLiveness(verificationAuth?.authToken, '');
        }
      }
    }
  }, [route && route?.params]);

  const handleLiveness = async () => {
    if (!endpoint.length || !apiKey.length) {
      Alert.alert(
        'Error',
        'Please set Face API Endpoint and key from Settings first',
      );
      return;
    }

    const res = await dispatch(
      detectLiveness({
        baseUrl: endpoint,
        key: apiKey,
        body: {
          livenessOperationMode: isActivePassive,
          deviceCorrelationId: '1234567890',
        },
      }),
    ).unwrap();
    if (res?.data?.authToken) {
      startLiveness(res?.data?.authToken, 'true');
    }
  };

  const handleLivenessWithVerification = () => {
    if (!endpoint.length || !apiKey.length) {
      Alert.alert(
        'Error',
        'Please set Face API Endpoint and key from Settings first',
      );
      return;
    }
    ImagePicker.openPicker({
      width: 450,
      height: 450,
      mediaType: 'photo',
      cropperCircleOverlay: false,
      sortOrder: 'none',
      compressImageQuality: 0.6,
      includeExif: true,
      cropping: false,
      includeBase64: true,
      cropperStatusBarColor: 'white',
      cropperToolbarColor: 'white',
      cropperActiveWidgetColor: 'white',
      freeStyleCropEnabled: true,
    })
      .then(async imgs => {
        setImage(imgs?.path);
        const formData = new FormData();
        formData.append('livenessOperationMode', isActivePassive);
        formData.append(
          'deviceCorrelationId',
          '00000000-0000-0000-0000-000000000000',
        );
        formData.append('verifyImage', {
          uri: imgs?.path,
          type: imgs?.mime,
          name: imgs?.filename || `${new Date().getTime()}.png`,
        });
        const res = await dispatch(
          detectLivenessWithVerification({
            baseUrl: endpoint,
            key: apiKey,
            body: formData,
          }),
        ).unwrap();
        if (res?.data?.authToken) {
          startLiveness(res?.data?.authToken, '');
        }
      })
      .catch(e => {
        console.log('openPicker_e: ', e);
      });
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.buttonContainer}>
        <TouchableOpacity onPress={handleLiveness} style={styles.button}>
          <Text style={styles.buttonText}>Liveness</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={handleLivenessWithVerification}
          style={styles.button}>
          <Text style={styles.buttonText}>Liveness with verification</Text>
        </TouchableOpacity>
      </View>
      <TouchableOpacity onPress={handleSettings} style={styles.settingsButton}>
        <Text style={styles.settingsText}>SETTINGS</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
};

export default HomeScreen;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    paddingVertical: 40,
    paddingHorizontal: 20,
  },
  buttonContainer: {
    marginBottom: 50,
  },
  button: {
    backgroundColor: '#F0F0F0',
    padding: 15,
    marginVertical: 10,
    width: '100%',
    alignItems: 'center',
  },
  buttonText: {
    color: '#000',
    fontSize: 18,
  },
  settingsButton: {
    position: 'absolute',
    bottom: 24,
    backgroundColor: '#3D5AFE',
    padding: 15,
    width: '100%',
    alignItems: 'center',
    alignSelf: 'center',
  },
  settingsText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
});

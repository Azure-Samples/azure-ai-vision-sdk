import React, {useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  Alert,
  StyleSheet,
} from 'react-native';
import CheckBox from '@react-native-community/checkbox';
import {useDispatch, useSelector} from 'react-redux';
import {
  setApiKey,
  setEndpoint,
  setActivePassive,
  setImageInClient,
} from '../redux/slices/globalSlice';

const SettingsScreen = () => {
  const dispatch = useDispatch();

  const endpoint = useSelector(state => state.global.endpoint);
  const faceAPIKey = useSelector(state => state.global.apiKey);
  const isActivePassive = useSelector(state => state.global.isActivePassive);
  const isImageInClient = useSelector(state => state.global.isImageInClient);

  const [apiEndpoint, setApiEndpoint] = useState(endpoint);
  const [apiKey, setApiKeyLocal] = useState(faceAPIKey);
  const [activePassive, changeActivePassive] = useState(
    isActivePassive === 'PassiveActive' ? true : false,
  );
  const [clientInImage, changeClientInImage] = useState(isImageInClient);

  const handleSave = () => {
    if (!apiEndpoint.trim() || !apiKey.trim()) {
      Alert.alert('Validation Error', 'Please fill in all fields.');
      return;
    }

    dispatch(setEndpoint(apiEndpoint));
    dispatch(setApiKey(apiKey));
    dispatch(setActivePassive(activePassive ? 'PassiveActive' : 'Passive'));
    dispatch(setImageInClient(clientInImage));
    Alert.alert('Success', 'Settings saved successfully!');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Face API endpoint:</Text>
      <TextInput
        style={styles.input}
        placeholder="https://"
        value={apiEndpoint}
        onChangeText={setApiEndpoint}
      />
      <Text style={styles.label}>Face API key:</Text>
      <TextInput
        style={[styles.input, {height: 80}]}
        placeholder="00000000000000000000000000000000"
        value={apiKey}
        multiline
        onChangeText={setApiKeyLocal}
        secureTextEntry
      />
      <Text style={styles.label}>Last session apim-request-id:</Text>
      <Text style={styles.placeholder}>[Your last session ID]</Text>

      <TouchableOpacity
        onPress={() => changeActivePassive(!activePassive)}
        style={styles.checkboxContainer}>
        <CheckBox
          disabled={true}
          value={activePassive}
          onValueChange={changeActivePassive}
          tintColors={{
            true: '#3D5AFE',
            false: '#3D5AFE',
          }}
        />
        <Text style={styles.checkboxLabel}>PassiveActive</Text>
      </TouchableOpacity>
      <TouchableOpacity
        onPress={() => changeClientInImage(!clientInImage)}
        style={styles.checkboxContainer}>
        <CheckBox
          disabled={true}
          value={clientInImage}
          onValueChange={changeClientInImage}
          tintColors={{
            true: '#3D5AFE',
            false: '#3D5AFE',
          }}
        />
        <Text style={styles.checkboxLabel}>setImageInClient</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.button} onPress={handleSave}>
        <Text style={styles.buttonText}>SAVE</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  label: {
    marginVertical: 10,
    fontSize: 16,
    color: '#000',
  },
  input: {
    height: 40,
    borderColor: 'gray',
    borderWidth: 1,
    paddingLeft: 10,
  },
  placeholder: {
    marginVertical: 10,
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
    position: 'absolute',
    bottom: 24,
    backgroundColor: '#3D5AFE',
    padding: 15,
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

export default SettingsScreen;

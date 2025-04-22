import 'dart:convert';
import 'dart:io';

import 'package:azurefacelivenessdetector/helper/shared_pref_helper.dart';
import 'package:azurefacelivenessdetector/services/file_picker_services.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:dio/dio.dart' as dio;
import 'package:permission_handler/permission_handler.dart';

class LivenessController extends GetxController {
  RxString sessionToken = ''.obs;
  RxString sessionId = ''.obs;
  RxString sessionType = ''.obs;

  RxBool isLoading = false.obs;
  RxBool isResultLoading = false.obs;

  RxString livenessStatusResult = ''.obs;
  RxString livenessFailureReasonResult = ''.obs;
  RxString livenessVerificationStatusResult = ''.obs;
  RxString livenessVerificationConfidenceResult = ''.obs;

  Rx<Uint8List?> verifyImageBytes = Rx<Uint8List?>(null);

  static const platform = MethodChannel('azure_face_liveness_channel');

  Future<Map<String, dynamic>> startLivenessDetection({
    required String sessionToken,
  }) async {
    try {
      if (Platform.isAndroid) {
        final granted = await Permission.camera.request().isGranted;
        if (!granted) {
          return {
            'status': 'error',
            'message': 'Camera permission denied',
          };
        }

        bool isSetImageInClient =
            SharedPrefsHelper.getBool('SETIMAGEINCLIENT') ?? false;

        bool verifyImageAvailable = verifyImageBytes.value != null;

        final result = await platform.invokeMethod("startLiveness", {
          "sessionToken": sessionToken,
          "verifyImage": isSetImageInClient &&
                  verifyImageAvailable &&
                  sessionType.value == 'detectLivenessWithVerify-sessions'
              ? verifyImageBytes.value
              : null, // or pass Uint8List
        });

        if (result != null && result is Map) {
          return Map<String, dynamic>.from(result);
        }

        return {
          'status': 'error',
          'message': 'Invalid result',
        };
      } else {
        final granted = await Permission.camera.request().isGranted;

        if (!granted) {
          await Permission.camera.request();
        }
        final lastesGrantedStatus = await Permission.camera.status.isGranted;

        if (lastesGrantedStatus) {
          final result = await platform.invokeMethod("startLiveness", {
            "sessionToken": sessionToken,
          });

          if (result != null && result is Map) {
            return Map<String, dynamic>.from(result);
          }

          return {
            'status': 'error',
            'message': 'Invalid result',
          };
        } else {
          return {
            'status': 'error',
            'message': 'Camera permission denied',
          };
        }
      }
    } on PlatformException catch (e) {
      return {
        'status': 'error',
        'message': e.toString(),
      };
    } catch (e) {
      return {
        'status': 'error',
        'message': e.toString(),
      };
    }
  }

  //
  Future<bool> handleGenerateTokenForDetectLivenessSessions() async {
    try {
      isLoading(true);

      String faceApiEndpoint =
          SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
      String faceApiKey = SharedPrefsHelper.getString('FACEAPIKEY') ?? '';
      bool isPassiveActive = SharedPrefsHelper.getBool('PASSIVEACTIVE') ?? true;

      final data = {
        "livenessOperationMode": isPassiveActive ? "PassiveActive" : "Passive",
        "deviceCorrelationId": "1234567890"
      };

      dio.Response response = await dio.Dio().post(
        '$faceApiEndpoint/face/v1.2/detectLiveness-sessions',
        options: dio.Options(
          headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            'Ocp-Apim-Subscription-Key': faceApiKey,
          },
        ),
        data: json.encode(data),
      );

      if (response.statusCode == 200) {
        if (response.data != null) {
          sessionType.value = 'detectLiveness-sessions';

          sessionId.value = response.data['sessionId'] ?? '';

          sessionToken.value = response.data['authToken'] ?? '';

          isLoading(false);

          return true;
        } else {
          // showErrorSnackBar('Something went wrong!');
          isLoading(false);

          return false;
        }
      } else {
        isLoading(false);

        return false;
      }
    } on dio.DioException catch (e) {
      Get.log('handleGenerateTokenForDetectLivenessSessions :: ${e.error}');
      isLoading(false);

      return false;
    }
  }

  Future<bool> handleGenerateTokenForDetectLivenessVerifiedSessions() async {
    try {
      isLoading(true);

      String faceApiEndpoint =
          SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
      String faceApiKey = SharedPrefsHelper.getString('FACEAPIKEY') ?? '';
      bool isPassiveActive = SharedPrefsHelper.getBool('PASSIVEACTIVE') ?? true;

      FilePickerResult pickImage = await FilePickerService().pickFile();

      if (pickImage.files.isEmpty) {
        return false;
      }

      String imagePath = pickImage.files.first.path!;

      String imageName = pickImage.files.first.name;

      final file = File(imagePath);
      verifyImageBytes.value = await file.readAsBytes();

      final formData = dio.FormData.fromMap({
        "livenessOperationMode": isPassiveActive ? "PassiveActive" : "Passive",
        'deviceCorrelationId': '1234567890',
        'verifyImage': await dio.MultipartFile.fromFile(
          imagePath,
          filename: imageName,
        ),
      });

      dio.Response response = await dio.Dio().post(
        '$faceApiEndpoint/face/v1.2/detectLivenessWithVerify-sessions',
        options: dio.Options(
          headers: {
            'Content-Type': 'multipart/form-data',
            'Ocp-Apim-Subscription-Key': faceApiKey,
          },
        ),
        data: formData,
      );

      if (response.statusCode == 200) {
        if (response.data != null) {
          sessionType.value = 'detectLivenessWithVerify-sessions';

          sessionId.value = response.data['sessionId'] ?? '';

          sessionToken.value = response.data['authToken'] ?? '';

          isLoading(false);

          return true;
        } else {
          // showErrorSnackBar('Something went wrong!');
          isLoading(false);

          return false;
        }
      } else {
        isLoading(false);

        return false;
      }
    } on dio.DioException catch (e) {
      Get.log(
          'handleGenerateTokenForDetectLivenessVerifiedSessions :dio.DioException: $e');
      isLoading(false);

      return false;
    } catch (e) {
      Get.log(
          'handleGenerateTokenForDetectLivenessVerifiedSessions :e: ${e.toString()}');
      isLoading(false);
      return false;
    }
  }

  //
  Future<bool> handleGetLivenessResult({required String resultType}) async {
    try {
      isResultLoading(true);

      String faceApiEndpoint =
          SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
      String faceApiKey = SharedPrefsHelper.getString('FACEAPIKEY') ?? '';

      dio.Response response = await dio.Dio().get(
        // 'https://harshil.cognitiveservices.azure.com//face/v1.2/detectLivenessWithVerify-sessions/f0b3b5be-62a6-4da0-b75c-a336ab999130',
        '$faceApiEndpoint/face/v1.2/${sessionType.value}/${sessionId.value}',
        options: dio.Options(
          headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            'Ocp-Apim-Subscription-Key': faceApiKey,
          },
        ),
      );

      if (response.statusCode == 200) {
        if (response.data != null) {
          if (resultType == 'SUCCESS') {
            livenessStatusResult.value = response.data['results']['attempts'][0]
                    ['result']['livenessDecision'] ??
                '';

            if (sessionType.value == 'detectLivenessWithVerify-sessions') {
              livenessVerificationStatusResult.value = response.data['results']
                      ['attempts'][0]['result']['verifyResult']['isIdentical']
                  .toString();
              livenessVerificationConfidenceResult.value = response
                  .data['results']['attempts'][0]['result']['verifyResult']
                      ['matchConfidence']
                  .toString();
            } else {
              ///detectLiveness-sessions
              livenessVerificationStatusResult.value = 'Succeeded';
            }
          } else if (resultType == 'ERROR|IOS') {
            livenessStatusResult.value =
                response.data['results']['attempts'][0]['error']['code'] ?? '';

            livenessFailureReasonResult.value =
                response.data['results']['attempts'][0]['error']['code'] ?? '';

            livenessVerificationStatusResult.value = 'None';
          }

          ///
          isResultLoading(false);

          return true;
        } else {
          // showErrorSnackBar('Something went wrong!');
          isResultLoading(false);

          return false;
        }
      } else {
        isResultLoading(false);

        return false;
      }
    } on dio.DioException catch (e) {
      Get.log('handleGetLivenessResult :: ${e.error}');
      isResultLoading(false);

      return false;
    }
  }
}

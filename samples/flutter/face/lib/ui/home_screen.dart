import 'dart:io';

import 'package:azurefacelivenessdetector/controller/liveness_controller.dart';
import 'package:azurefacelivenessdetector/helper/shared_pref_helper.dart';
import 'package:azurefacelivenessdetector/ui/result_screen.dart';
import 'package:azurefacelivenessdetector/ui/settings_screen.dart';
import 'package:azurefacelivenessdetector/widgets/common_snackbar.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final LivenessController livenessController = Get.find<LivenessController>();
  // static const platform = MethodChannel('azure_face_liveness_channel');

  String faceApiEndpoint = SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
  String faceApiKey = SharedPrefsHelper.getString('FACEAPIKEY') ?? '';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Obx(
        () => Center(
          child: livenessController.isLoading.value
              ? CircularProgressIndicator(
                  color: Colors.purple,
                )
              : Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: faceApiEndpoint.isNotEmpty &&
                                  faceApiKey.isNotEmpty
                              ? Colors.purple
                              : Colors.grey,
                          foregroundColor: Colors.white,
                          alignment: Alignment.center,
                        ),
                        onPressed: () {
                          if (faceApiEndpoint.isNotEmpty &&
                              faceApiKey.isNotEmpty) {
                            livenessController
                                .handleGenerateTokenForDetectLivenessSessions()
                                .then((value) {
                              if (value) {
                                livenessController
                                    .startLivenessDetection(
                                  sessionToken:
                                      livenessController.sessionToken.value,
                                )
                                    .then((result) async {
                                  if (result['status'] == 'success') {
                                    ///
                                    Get.to(
                                      () => ResultScreen(
                                        resultType: 'SUCCESS',
                                      ),
                                    );
                                  } else if (result['status'] == 'error' &&
                                      result['message'] ==
                                          'Camera permission denied') {
                                    await Permission.camera.request();
                                  } else {
                                    if (Platform.isAndroid) {
                                      livenessController
                                              .livenessStatusResult.value =
                                          result['livenessError'] ?? '';
                                      livenessController
                                              .livenessFailureReasonResult
                                              .value =
                                          result['livenessError'] ?? '';
                                      livenessController
                                              .livenessVerificationStatusResult
                                              .value =
                                          result['recognitionError'] ?? '';

                                      Get.to(
                                        () => ResultScreen(
                                          resultType: 'ERROR',
                                        ),
                                      );
                                    } else {
                                      Get.to(
                                        () => ResultScreen(
                                          resultType: 'ERROR|IOS',
                                        ),
                                      );
                                    }
                                  }
                                });
                              }
                            });
                          }
                        },
                        child: Text(
                          'Liveness',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ),
                    SizedBox(
                      height: 24,
                    ),
                    SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: faceApiEndpoint.isNotEmpty &&
                                  faceApiKey.isNotEmpty
                              ? Colors.purple
                              : Colors.grey,
                          foregroundColor: Colors.white,
                          alignment: Alignment.center,
                        ),
                        onPressed: () {
                          ///
                          if (faceApiEndpoint.isNotEmpty &&
                              faceApiKey.isNotEmpty) {
                            livenessController
                                .handleGenerateTokenForDetectLivenessVerifiedSessions()
                                .then(
                              (value) {
                                if (value) {
                                  ///
                                  livenessController
                                      .startLivenessDetection(
                                    sessionToken:
                                        livenessController.sessionToken.value,
                                  )
                                      .then((result) {
                                    if (result['status'] == 'success') {
                                      ///
                                      Get.to(
                                        () => ResultScreen(
                                          resultType: 'SUCCESS',
                                        ),
                                      );
                                    } else if (result['status'] == 'error' &&
                                        result['message'] ==
                                            'Camera permission denied') {
                                      showErrorSnackBar(
                                          'Camera permission denied');
                                    } else {
                                      if (Platform.isAndroid) {
                                        livenessController
                                                .livenessStatusResult.value =
                                            result['livenessError'] ?? '';
                                        livenessController
                                                .livenessFailureReasonResult
                                                .value =
                                            result['livenessError'] ?? '';
                                        livenessController
                                            .livenessVerificationStatusResult
                                            .value = result[
                                                'recognitionError'] ??
                                            '';

                                        Get.to(
                                          () => ResultScreen(
                                            resultType: 'ERROR',
                                          ),
                                        );
                                      } else {
                                        Get.to(
                                          () => ResultScreen(
                                            resultType: 'ERROR|IOS',
                                          ),
                                        );
                                      }
                                    }
                                  });
                                }
                              },
                            );
                          }
                        },
                        child: Text(
                          'Liveness with verification',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    )
                  ],
                ),
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: SizedBox(
        height: 48,
        child: ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.purple,
            foregroundColor: Colors.white,
            alignment: Alignment.center,
          ),
          onPressed: () {
            Get.to(
              () => SettingScreen(),
            )?.then((value) {
              faceApiEndpoint =
                  SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
              faceApiKey = SharedPrefsHelper.getString('FACEAPIKEY') ?? '';
              setState(() {});
            });
          },
          child: Text(
            'Settings',
            style: TextStyle(
              fontSize: 16,
            ),
          ),
        ),
      ),
    );
  }
}

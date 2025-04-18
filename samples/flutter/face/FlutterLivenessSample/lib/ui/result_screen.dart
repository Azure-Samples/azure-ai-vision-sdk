import 'package:azurefacelivenessdetector/controller/liveness_controller.dart';
import 'package:azurefacelivenessdetector/ui/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class ResultScreen extends StatefulWidget {
  final String? resultType;
  const ResultScreen({
    super.key,
    required this.resultType,
  });

  @override
  State<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends State<ResultScreen> {
  LivenessController livenessController = Get.find<LivenessController>();

  @override
  void initState() {
    if (widget.resultType == 'SUCCESS' || widget.resultType == 'ERROR|IOS') {
      livenessController.handleGetLivenessResult(
          resultType: widget.resultType.toString());
    }
    super.initState();
  }

  @override
  void dispose() {
    ///
    livenessController.livenessStatusResult.value = '';
    livenessController.livenessFailureReasonResult.value = '';
    livenessController.livenessVerificationStatusResult.value = '';
    livenessController.livenessVerificationConfidenceResult.value = '';
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Obx(
        () => livenessController.isResultLoading.value
            ? Center(
                child: CircularProgressIndicator(
                  color: Colors.purple,
                ),
              )
            : SingleChildScrollView(
                child: Container(
                  padding: EdgeInsets.symmetric(horizontal: 24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.start,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      SizedBox(
                        height: 60,
                      ),

                      Text(
                        'Results',
                        style: TextStyle(
                          fontSize: 28,
                        ),
                      ),

                      SizedBox(
                        height: 30,
                      ),

                      ///
                      ResultTextWidget(
                        title: 'Liveness status:',
                        resultText:
                            livenessController.livenessStatusResult.value,
                      ),

                      SizedBox(
                        height: 24,
                      ),

                      ///
                      if (widget.resultType == 'ERROR' ||
                          widget.resultType == 'ERROR|IOS')
                        ResultTextWidget(
                          title: 'Liveness failure reason:',
                          resultText: livenessController
                              .livenessFailureReasonResult.value,
                        ),

                      if (widget.resultType == 'ERROR' ||
                          widget.resultType == 'ERROR|IOS')
                        SizedBox(
                          height: 24,
                        ),

                      ///
                      ResultTextWidget(
                        title: 'Verification status:',
                        resultText: livenessController
                            .livenessVerificationStatusResult.value,
                      ),

                      SizedBox(
                        height: 24,
                      ),

                      ///
                      if (widget.resultType == 'SUCCESS' &&
                          livenessController.sessionType.value ==
                              'detectLivenessWithVerify-sessions' &&
                          livenessController.verifyImageBytes.value != null)
                        ResultTextWidget(
                          title: 'Verification confidence:',
                          resultText: livenessController
                              .livenessVerificationConfidenceResult.value,
                        ),
                    ],
                  ),
                ),
              ),
      ),
      bottomNavigationBar: Container(
        margin: EdgeInsets.only(
          left: 24,
          right: 24,
          bottom: 24,
          top: 8,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              // width: double.infinity,
              height: 48,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.purple,
                  foregroundColor: Colors.white,
                  alignment: Alignment.center,
                ),
                onPressed: () {
                  // Navigator.pushAndRemoveUntil(
                  //   context,
                  //   MaterialPageRoute(builder: (context) => HomeScreen()),
                  //   (route) => false, // Removes all previous routes
                  // );

                  Get.offAll(() => HomeScreen());

                  // livenessController.handleGetLivenessResult(
                  //     resultType: 'ERROR|IOS');
                },
                child: Text(
                  'Go to the main screen',
                  style: TextStyle(
                    fontSize: 16,
                  ),
                ),
              ),
            ),
            SizedBox(
              height: 16,
            ),
            SizedBox(
              // width: double.infinity,
              height: 48,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.purple,
                  foregroundColor: Colors.white,
                  alignment: Alignment.center,
                ),
                onPressed: () {
                  Get.back();

                  ///
                  livenessController
                      .startLivenessDetection(
                    sessionToken: livenessController.sessionToken.value,
                  )
                      .then((result) {
                    if (result['status'] == 'success') {
                      ///
                      Get.to(
                        () => ResultScreen(
                          resultType: 'SUCCESS',
                        ),
                      );
                    } else {
                      livenessController.livenessStatusResult.value =
                          result['livenessError'] ?? '';
                      livenessController.livenessFailureReasonResult.value =
                          result['livenessError'] ?? '';
                      livenessController.livenessVerificationStatusResult
                          .value = result['recognitionError'] ?? '';

                      Get.to(
                        () => ResultScreen(
                          resultType: 'ERROR',
                        ),
                      );
                    }
                  });
                  // Navigator.pop(context);
                },
                child: Text(
                  'Retry with the same token',
                  style: TextStyle(
                    fontSize: 16,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ResultTextWidget extends StatelessWidget {
  final String title;
  final String resultText;
  const ResultTextWidget({
    super.key,
    required this.title,
    required this.resultText,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.start,
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
          ),
        ),
        SizedBox(
          height: 8,
        ),
        Text(
          resultText,
          style: TextStyle(
            fontSize: 16,
          ),
        ),
      ],
    );
  }
}

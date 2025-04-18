import 'package:azurefacelivenessdetector/controller/liveness_controller.dart';
import 'package:azurefacelivenessdetector/helper/shared_pref_helper.dart';
import 'package:azurefacelivenessdetector/widgets/common_snackbar.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SettingScreen extends StatefulWidget {
  const SettingScreen({super.key});

  @override
  State<SettingScreen> createState() => _SettingScreenState();
}

class _SettingScreenState extends State<SettingScreen> {
  LivenessController livenessController = Get.find<LivenessController>();

  TextEditingController faceApiEndpointTextController = TextEditingController();
  TextEditingController faceApiKeyTextController = TextEditingController();

  bool isSetImageInClient = false;
  bool isPassiveActive = false;

  bool isObsecureApiKey = true;

  @override
  void initState() {
    super.initState();

    handleReadandSetData();
  }

  handleReadandSetData() {
    faceApiEndpointTextController.text =
        SharedPrefsHelper.getString('FACEAPIENDPOINT') ?? '';
    faceApiKeyTextController.text =
        SharedPrefsHelper.getString('FACEAPIKEY') ?? '';

    isSetImageInClient = SharedPrefsHelper.getBool('SETIMAGEINCLIENT') ?? false;
    isPassiveActive = SharedPrefsHelper.getBool('PASSIVEACTIVE') ?? false;

    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // appBar: AppBar(
      //   backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      //   title: Text('widget.title'),
      // ),
      body: SingleChildScrollView(
        child: Container(
          padding: EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              SizedBox(
                height: 100,
              ),

              ///
              Text(
                'Face API endpoint:',
                style: TextStyle(
                  fontSize: 18,
                ),
              ),
              SizedBox(
                height: 12,
              ),
              TextField(
                controller: faceApiEndpointTextController,
                minLines: 2,
                maxLines: 3,
                decoration: InputDecoration(
                  hintText: "https://",
                  filled: true,
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(
                      color: Colors.black,
                    ), // Underline color
                  ),
                  focusedBorder: UnderlineInputBorder(
                    borderSide: BorderSide(
                      color: Colors.purple,
                      width: 2.0,
                    ), // Thicker underline when focused
                  ),
                ),
              ),
              SizedBox(
                height: 30,
              ),

              ///
              Text(
                'Face API key:',
                style: TextStyle(
                  fontSize: 18,
                ),
              ),
              SizedBox(
                height: 12,
              ),
              TextField(
                controller: faceApiKeyTextController,
                obscureText: isObsecureApiKey,
                decoration: InputDecoration(
                  hintText: "0000000000000000000000000000000000",
                  filled: true,
                  suffixIcon: InkWell(
                    onTap: () {
                      isObsecureApiKey = !isObsecureApiKey;

                      setState(() {});
                    },
                    child: Icon(
                      isObsecureApiKey
                          ? Icons.visibility_off
                          : Icons.visibility,
                    ),
                  ),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(
                      color: Colors.black,
                    ), // Underline color
                  ),
                  focusedBorder: UnderlineInputBorder(
                    borderSide: BorderSide(
                      color: Colors.purple,
                      width: 2.0,
                    ), // Thicker underline when focused
                  ),
                ),
              ),
              SizedBox(
                height: 30,
              ),

              ///
              Text(
                'Last Session apim-request-id',
                style: TextStyle(
                  fontSize: 18,
                ),
              ),
              SizedBox(
                height: 12,
              ),
              Obx(
                () => Text(
                  livenessController.sessionId.value,
                  style: TextStyle(
                    fontSize: 18,
                  ),
                ),
              ),
              SizedBox(
                height: 30,
              ),

              ///
              Row(
                children: [
                  Checkbox(
                    value: isSetImageInClient,
                    onChanged: (bool? value) {
                      setState(() {
                        isSetImageInClient = value!;
                      });
                    },
                  ),
                  Text(
                    'setImageInClient',
                    style: TextStyle(
                      fontSize: 18,
                    ),
                  ),
                ],
              ),
              SizedBox(
                height: 30,
              ),
              Row(
                children: [
                  Checkbox(
                    value: isPassiveActive,
                    onChanged: (bool? value) {
                      setState(() {
                        isPassiveActive = value!;
                      });
                    },
                  ),
                  Text(
                    'PassiveActive',
                    style: TextStyle(
                      fontSize: 18,
                    ),
                  ),
                ],
              ),
            ],
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
        height: 48,
        child: ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.purple,
            foregroundColor: Colors.white,
            alignment: Alignment.center,
          ),
          onPressed: () async {
            if (faceApiEndpointTextController.text.trim().toString().isEmpty) {
              showErrorSnackBar(
                'Please enter Face API endpoint',
              );
              return;
            }

            if (faceApiKeyTextController.text.trim().toString().isEmpty) {
              showErrorSnackBar(
                'Please enter Face API key',
              );
              return;
            }

            await SharedPrefsHelper.setString('FACEAPIENDPOINT',
                faceApiEndpointTextController.text.trim().toString());
            await SharedPrefsHelper.setString(
                'FACEAPIKEY', faceApiKeyTextController.text.trim().toString());
            await SharedPrefsHelper.setBool(
                'SETIMAGEINCLIENT', isSetImageInClient);
            await SharedPrefsHelper.setBool('PASSIVEACTIVE', isPassiveActive);

            // Navigator.pop(context);
            Get.back();
          },
          child: Text(
            'SAVE',
            style: TextStyle(
              fontSize: 18,
            ),
          ),
        ),
      ),
    );
  }
}

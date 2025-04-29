import 'package:azurefacelivenessdetector/controller/liveness_controller.dart';
import 'package:azurefacelivenessdetector/helper/shared_pref_helper.dart';
import 'package:azurefacelivenessdetector/ui/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

void main() async {
  ///
  WidgetsFlutterBinding.ensureInitialized();
  await SharedPrefsHelper.init();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return GetMaterialApp(
      title: 'FaceLivenessDetector',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      initialBinding: BindingsBuilder(() {
        Get.put<LivenessController>(LivenessController());
      }),
      home: const HomeScreen(),
    );
  }
}

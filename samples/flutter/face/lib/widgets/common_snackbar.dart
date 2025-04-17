import 'package:flutter/material.dart';
import 'package:get/get.dart';

void showErrorSnackBar(String message) {
  Get.showSnackbar(
    GetSnackBar(
      margin: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 10,
      ),
      borderRadius: 12,
      overlayBlur: 2,
      messageText: Text(
        message,
        style: TextStyle(
          color: Colors.white,
          fontSize: 16,
        ),
      ),
      backgroundColor: Colors.red,
      snackPosition: SnackPosition.TOP,
      duration: const Duration(milliseconds: 2000),
      animationDuration: const Duration(milliseconds: 300),
    ),
  );
}

void showSuccessSnackBar(
  String message,
) {
  Get.showSnackbar(
    GetSnackBar(
      margin: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 10,
      ),
      borderRadius: 12,
      overlayBlur: 2,
      messageText: Text(
        message,
        style: TextStyle(
          color: Colors.white,
          fontSize: 16,
        ),
      ),
      backgroundColor: Colors.green,
      snackPosition: SnackPosition.TOP,
      duration: const Duration(milliseconds: 2000),
      animationDuration: const Duration(milliseconds: 300),
    ),
  );
}

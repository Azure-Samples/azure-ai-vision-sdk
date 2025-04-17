import 'package:shared_preferences/shared_preferences.dart';

class SharedPrefsHelper {
  static SharedPreferences? _prefs;

  // Initialize once before using
  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
  }

  // Setters
  static Future<void> setString(String key, String value) async {
    await _prefs?.setString(key, value);
  }

  static Future<void> setInt(String key, int value) async {
    await _prefs?.setInt(key, value);
  }

  static Future<void> setBool(String key, bool value) async {
    await _prefs?.setBool(key, value);
  }

  static Future<void> setDouble(String key, double value) async {
    await _prefs?.setDouble(key, value);
  }

  static Future<void> setStringList(String key, List<String> value) async {
    await _prefs?.setStringList(key, value);
  }

  // Getters
  static String? getString(String key) => _prefs?.getString(key);
  static int? getInt(String key) => _prefs?.getInt(key);
  static bool? getBool(String key) => _prefs?.getBool(key);
  static double? getDouble(String key) => _prefs?.getDouble(key);
  static List<String>? getStringList(String key) => _prefs?.getStringList(key);

  // Remove a value
  static Future<void> remove(String key) async {
    await _prefs?.remove(key);
  }

  // Clear all
  static Future<void> clear() async {
    await _prefs?.clear();
  }
}

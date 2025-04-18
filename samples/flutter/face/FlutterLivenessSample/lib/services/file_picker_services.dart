import 'package:file_picker/file_picker.dart';

class FilePickerService {
  Future<FilePickerResult> pickFile() async {
    // Request storage permission
    // final permissionStatus = await Permission.storage.request();

    // if (permissionStatus.isGranted) {
    // Open file picker limited to image files
    final result = await FilePicker.platform.pickFiles(
      type: FileType.image,
      allowMultiple: false,
      // withData: true,
    );

    if (result != null && result.files.single.path != null) {
      // Get.log('Picked image: ${result.files.single.path!}');
      return result;
    } else {
      return FilePickerResult([]);
    }
  }
}

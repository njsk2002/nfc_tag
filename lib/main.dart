import 'package:flutter/material.dart';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

void main() {
  runApp(MaterialApp(home: NFCPage()));
}

class NFCPage extends StatefulWidget {
  @override
  _NFCPageState createState() => _NFCPageState();
}

class _NFCPageState extends State<NFCPage> {
  static const platform = MethodChannel('kr.co.daram.nfc_tag');
  String _status = "Idle";
  double _progress = 0.0;

  Uint8List? _imageBytes;
  int _imageWidth = 0;
  int _imageHeight = 0;
  int _selectedSize = 0; // 2 for 2.9-inch, 3 for 4.2-inch

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text("NFC to E-Ink Display")),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text("Status: $_status", style: TextStyle(fontSize: 16)),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: () => _selectDisplaySize(2),
                  child: Text("2.9-inch (296x128)"),
                ),
                const SizedBox(width: 10),
                ElevatedButton(
                  onPressed: () => _selectDisplaySize(3),
                  child: Text("4.2-inch (400x300)"),
                ),
              ],
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _selectImage,
              child: Text("Select Image"),
            ),
            ElevatedButton(
              onPressed: _enterText,
              child: Text("Enter Text"),
            ),
            ElevatedButton(
              onPressed: _startNFCProcess,
              child: Text("Start NFC Process"),
            ),
            const SizedBox(height: 20),
            if (_imageBytes != null)
              Column(
                children: [
                  Text("Image Preview:"),
                  const SizedBox(height: 10),
                  Image.memory(_imageBytes!, width: _imageWidth.toDouble(), height: _imageHeight.toDouble()),
                ],

              ),
          ],
        ),
      ),
    );
  }

  void _selectDisplaySize(int size) {
    setState(() {
      _selectedSize = size;
      _status = size == 2
          ? "2.9-inch display selected (296x128)"
          : "4.2-inch display selected (400x300)";
    });
  }

  Future<void> _selectImage() async {
    if (_selectedSize == 0) {
      setState(() {
        _status = "Please select a display size first.";
      });
      return;
    }

    final picker = ImagePicker();
    final image = await picker.pickImage(source: ImageSource.gallery);

    if (image != null) {
      final imageBytes = await image.readAsBytes();
      final resizedImage = await _renderImageToImage(imageBytes);
      //final resizedImage = await _renderImageToBMP(imageBytes);

     // PNG 파일 사용시
      final decodedImage = await decodeImageFromList(resizedImage);

      setState(() {
        _imageBytes = resizedImage;
        _imageWidth = decodedImage.width;
        _imageHeight = decodedImage.height;
        _status = "Image Selected: ${image.name} (${_imageWidth}x${_imageHeight})";
      });

      // setState(() {
      //   _imageBytes = resizedImage;
      //   _imageWidth = _selectedSize == 2 ? 296 : 400; // 고정된 크기 설정
      //   _imageHeight = _selectedSize == 2 ? 128 : 300; // 고정된 크기 설정
      //   _status = "Image Selected: ${image.name}";
      // });
    }
  }

  Future<void> _enterText() async {
    if (_selectedSize == 0) {
      setState(() {
        _status = "Please select a display size first.";
      });
      return;
    }

    TextEditingController controller = TextEditingController();
    String? enteredText = await showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text("Enter Text"),
          content: TextField(controller: controller),
          actions: [
            ElevatedButton(
              onPressed: () => Navigator.of(context).pop(controller.text),
              child: Text("Submit"),
            ),
          ],
        );
      },
    );

    if (enteredText != null && enteredText.isNotEmpty) {
      final textImage = await _renderTextToImage(enteredText);
      final decodedImage = await decodeImageFromList(textImage);

      setState(() {
        _imageBytes = textImage;
        _imageWidth = decodedImage.width;
        _imageHeight = decodedImage.height;
        _status = "Text Image Prepared (${_imageWidth}x${_imageHeight})";
      });
    }
  }

  Future<Uint8List> _renderTextToImage(String text) async {
    final width = _selectedSize == 2 ? 296 : 400;
    final height = _selectedSize == 2 ? 128 : 300;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder, Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()));
    final textStyle = TextStyle(color: Colors.white, fontSize: 20);
    final textSpan = TextSpan(text: text, style: textStyle);
    final textPainter = TextPainter(
      text: textSpan,
      textDirection: TextDirection.ltr,
    );
    textPainter.layout(minWidth: 0, maxWidth: width.toDouble());
    textPainter.paint(canvas, Offset(10, (height / 2) - 10));
    final picture = recorder.endRecording();
    final image = await picture.toImage(width, height);
    final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
    return Uint8List.fromList(byteData!.buffer.asUint8List());
  }

  Future<Uint8List> _renderImageToBMP(Uint8List imageBytes) async {
    final width = _selectedSize == 2 ? 296 : 400;
    final height = _selectedSize == 2 ? 128 : 300;

    final codec = await ui.instantiateImageCodec(imageBytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;

    final cropWidth = width;
    final cropHeight = height;
    final imageWidth = image.width;
    final imageHeight = image.height;

    final cropX = (imageWidth > cropWidth) ? (imageWidth - cropWidth) ~/ 2 : 0;
    final cropY = 0; // Top-center alignment

    final cropRect = Rect.fromLTWH(
      cropX.toDouble(),
      cropY.toDouble(),
      cropWidth.toDouble(),
      cropHeight.toDouble(),
    );

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder, Rect.fromLTWH(0, 0, cropWidth.toDouble(), cropHeight.toDouble()));

    final paint = Paint()
      ..colorFilter = const ColorFilter.matrix([
        0.2126, 0.7152, 0.0722, 0, 0, // Red channel
        0.2126, 0.7152, 0.0722, 0, 0, // Green channel
        0.2126, 0.7152, 0.0722, 0, 0, // Blue channel
        0, 0, 0, 1, 0,                 // Alpha channel
      ]);

    canvas.drawImageRect(image, cropRect, Rect.fromLTWH(0, 0, cropWidth.toDouble(), cropHeight.toDouble()), paint);

    final picture = recorder.endRecording();
    final croppedImage = await picture.toImage(cropWidth, cropHeight);

    final byteData = await croppedImage.toByteData(format: ui.ImageByteFormat.rawRgba);

    if (byteData == null) throw Exception("Failed to convert image to BMP");

    return _convertToGrayBMP(byteData.buffer.asUint8List(), cropWidth, cropHeight);
  }

  Uint8List _convertToGrayBMP(Uint8List rawRgba, int width, int height) {
    final bmpHeaderSize = 54;
    final pixelDataSize = width * height; // 1 byte per pixel for grayscale
    final fileSize = bmpHeaderSize + pixelDataSize;
    final header = ByteData(bmpHeaderSize);

    // BMP Header
    header.setUint8(0, 0x42); // 'B'
    header.setUint8(1, 0x4D); // 'M'
    header.setUint32(2, fileSize, Endian.little); // File size
    header.setUint32(6, 0); // Reserved
    header.setUint32(10, bmpHeaderSize, Endian.little); // Offset to pixel array

    // DIB Header
    header.setUint32(14, 40, Endian.little); // DIB header size
    header.setUint32(18, width, Endian.little); // Image width
    header.setUint32(22, -height, Endian.little); // Image height (negative for top-down BMP)
    header.setUint16(26, 1, Endian.little); // Color planes
    header.setUint16(28, 8, Endian.little); // Bits per pixel (8 for grayscale)
    header.setUint32(30, 0, Endian.little); // Compression (0 = none)
    header.setUint32(34, pixelDataSize, Endian.little); // Image size
    header.setUint32(38, 2835, Endian.little); // Horizontal resolution (72 DPI × 39.3701 inches per meter)
    header.setUint32(42, 2835, Endian.little); // Vertical resolution (72 DPI × 39.3701 inches per meter)
    header.setUint32(46, 256, Endian.little); // Number of colors in palette
    header.setUint32(50, 256, Endian.little); // Important colors

    // Grayscale color palette (256 shades of gray)
    final palette = Uint8List(256 * 4); // 256 colors × 4 bytes per color (RGBA)
    for (int i = 0; i < 256; i++) {
      palette[i * 4] = i; // Blue
      palette[i * 4 + 1] = i; // Green
      palette[i * 4 + 2] = i; // Red
      palette[i * 4 + 3] = 0; // Alpha
    }

    // Convert RGBA to grayscale pixel data
    final grayscalePixels = Uint8List(pixelDataSize);
    for (int i = 0; i < rawRgba.length; i += 4) {
      final r = rawRgba[i];
      final g = rawRgba[i + 1];
      final b = rawRgba[i + 2];
      final gray = (0.2126 * r + 0.7152 * g + 0.0722 * b).round();
      grayscalePixels[i ~/ 4] = gray;
    }

    return Uint8List.fromList(header.buffer.asUint8List() + palette + grayscalePixels);
  }



  Future<Uint8List> _renderImageToImage(Uint8List imageBytes) async {
    final width = _selectedSize == 2 ? 296 : 400;
    final height = _selectedSize == 2 ? 128 : 300;

    final codec = await ui.instantiateImageCodec(imageBytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;

    final cropWidth = width;
    final cropHeight = height;
    final imageWidth = image.width;
    final imageHeight = image.height;

    final cropX = (imageWidth > cropWidth) ? (imageWidth - cropWidth) ~/ 2 : 0;
    final cropY = 0; // Top-center alignment

    final cropRect = Rect.fromLTWH(
      cropX.toDouble(),
      cropY.toDouble(),
      cropWidth.toDouble(),
      cropHeight.toDouble(),
    );

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder, Rect.fromLTWH(0, 0, cropWidth.toDouble(), cropHeight.toDouble()));

    //기존 색상 유지
    // final paint = Paint();
    // canvas.drawImageRect(image, cropRect, Rect.fromLTWH(0, 0, cropWidth.toDouble(), cropHeight.toDouble()), paint);

    // 블객으로 변경 Create a paint object with a grayscale color filter
    final paint = Paint()
      ..colorFilter = const ColorFilter.matrix([
        0.2126, 0.7152, 0.0722, 0, 0, // Red channel
        0.2126, 0.7152, 0.0722, 0, 0, // Green channel
        0.2126, 0.7152, 0.0722, 0, 0, // Blue channel
        0, 0, 0, 1, 0,                 // Alpha channel
      ]);

    canvas.drawImageRect(image, cropRect, Rect.fromLTWH(0, 0, cropWidth.toDouble(), cropHeight.toDouble()), paint);


    final picture = recorder.endRecording();
    final croppedImage = await picture.toImage(cropWidth, cropHeight);

    final byteData = await croppedImage.toByteData(format: ui.ImageByteFormat.png);
    return Uint8List.fromList(byteData!.buffer.asUint8List());
  }


  Future<void> _startNFCProcess() async {
    try {
      if (_imageBytes == null) {
        setState(() => _status = "No image or text selected");
        return;
      }

      if (_selectedSize == 0) {
        setState(() => _status = "Please select a display size first.");
        return;
      }

      setState(() {
        _status = "Initializing NFC...";
        _progress = 0.0;
      });

      final result = await platform.invokeMethod('startNFCProcess', {
        "imageData": _imageBytes,
        "displaySize": _selectedSize,
      });

      if (result == true) {
        setState(() => _status = "NFC Process Completed");
      } else {
        setState(() => _status = "NFC Process Failed");
      }
    } catch (e) {
      setState(() => _status = "Error: $e");
    }
  }
}

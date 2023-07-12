import 'dart:core';
import 'dart:io';
import 'dart:typed_data';
import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_gallery_saver/image_gallery_saver.dart';
import 'src/call_sample/signaling.dart';
import 'src/utils/device_info.dart';
import 'dart:developer';

void main() => runApp(new MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: const MyHomePage(title: 'WebRTC Drop Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  final String title;

  const MyHomePage({
    Key? key,
    required this.title,
  }) : super(key: key);

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform = MethodChannel('samples.flutter.dev/ble');

  Signaling? _signaling;
  List<dynamic> _peers = [];
  String? _selfId;
  String? _peerId;
  bool _inCalling = false;
  RTCDataChannel? _dataChannel;
  Session? _session;
  Timer? _timer;
  var _text = '';

  String params = '---';
  final list = [];
  int listindex = 0;

  // ignore: unused_element

  Image? _image;
  Image? _image2;
  XFile? _xfiles;

  _MyHomePageState();

  String remotePeerName = '';
  String receivedContent = '';

  @override
  deactivate() {
    super.deactivate();
    _signaling?.close();
    _timer?.cancel();
  }

  Future<dynamic> _platformCallHandler(MethodCall call) async {
    switch (call.method) {
      case 'receive':
        String content = call.arguments;
        int length = content.length;
        if (content.substring(length-3,length) != 'fin') {
          setState(() {
            receivedContent = receivedContent + content;
          });
          break;
        } else {
          setState(() {
            receivedContent = receivedContent + content.substring(0,length-3);
          });
          //print('call callMe : arguments = ' + receivedContent);
          _signaling?.onMessage(receivedContent);

          Map<String, dynamic> mapData = jsonDecode(receivedContent);
          setState(() {
            receivedContent = '';
          });
          if (mapData['type'] == 'IdOffer') {
            setState(() {
              remotePeerName = mapData['data']['myName'];
              _peerId = mapData['data']['Id'];
            });
            //確認ダイアログをだしてOKだったらIdAnswer
            bool answer = await showDialog(
                context: context,
                builder: (_) {
                  return PeerConfirmDialog(remotePeerName);
                });
            if (answer == true) {
              log('created Answer !!'+remotePeerName);
              _signaling?.createIdAnswer(remotePeerName);
            } else {
              log('not created');
            }
            log('answer = $answer');
          } else if (mapData['type'] == 'IdAnswer') {
            setState(() {
              remotePeerName = mapData['data']['myName'];
              _peerId = mapData['data']['Id'];
            });
            _invitePeer(context, _peerId);
          }
          break;
        }

      case 'ScanResultTerminalMap':
        setState(() {
          print('${call.arguments}');
          Map<String, dynamic> terminalMap = jsonDecode(call.arguments);
          list.clear();
          terminalMap.forEach((k, v) => list.add(Device(k, v)));
        });
        break;
      default:
        print('Unknowm method ${call.method}');
        throw MissingPluginException();
        break;
    }
  }

  void setIndex(int n) {
    listindex = n;
  }

  void _connect(BuildContext context, String address) async {
    await _signaling?.BLEConnect(address);
    //_signaling?.createIdOffer(DeviceInfo.label, address);
    List<int> list = <int>[];
    Uint8List list8 = Uint8List(0);



    // _signaling?.onPeersUpdate = ((event) {
    //   setState(() {
    //     _selfId = event['self'];
    //     _peers = event['peers'];
    //   });
    // });
  }

  _invitePeer(context, peerId) async {
    if (peerId != _selfId) {
      _signaling?.invite(peerId, 'data', false);
    }
  }

  _hangUp() {
    _signaling?.bye(_session!.sid);
  }

  // _buildRow(context, peer) {
  //   var self = (peer['id'] == _selfId);
  //   return ListBody(children: <Widget>[
  //     ListTile(
  //       title: Text(self
  //           ? peer['name'] + ', ID: ${peer['id']} ' + ' [Your self]'
  //           : peer['name'] + ', ID: ${peer['id']} '),
  //       onTap: () => _invitePeer(context, peer['id']),
  //       trailing: Icon(Icons.sms),
  //       subtitle: Text('[' + peer['user_agent'] + ']'),
  //     ),
  //     Divider()
  //   ]);
  // }

  void _uploadPicture(Image? image) {
    setState(() {
      _image = image;
    });
  }

  Future<void> _sendBinary() async {
    Uint8List byte = await _xfiles!.readAsBytes();
    int length = byte.length;
    int listnow = 0;
    for (int i = 250000;
        i < length;
        listnow = listnow + 250000, i = i + 250000) {
      await _dataChannel
          ?.send(RTCDataChannelMessage.fromBinary(byte.sublist(listnow, i)));
      await Future.delayed(Duration(milliseconds: 100)); //相手が受け取って処理する時間を与える
    }
    await _dataChannel
        ?.send(RTCDataChannelMessage.fromBinary(byte.sublist(listnow, length)));
    await Future.delayed(Duration(milliseconds: 100));
    await _dataChannel?.send(RTCDataChannelMessage('finish'));
  }

  Future _saveImage2(Uint8List _buffer) async {
    final result = await ImageGallerySaver.saveImage(_buffer);
  }

  void _viewImage2(Uint8List list) {
    setState(() {
      _image2 = Image.memory(list);
    });
  }

  Future<Image?> getPictureImage_mobile() async {
    Image? image;
    ImagePicker picker = ImagePicker();
    XFile? xfile = await picker.pickImage(source: ImageSource.gallery);
    setState(() {
      _xfiles = xfile;
    });
    if (xfile != null) {
      File file = File(xfile.path);
      image = Image.file(file);
    }
    return image;
  }

  @override
  initState() {
    super.initState();
    _signaling ??= Signaling(platform, context); //インスタンス化
    platform
        .setMethodCallHandler(_platformCallHandler); //Kotlinからデータを受け取るハンドラをセット
    _signaling?.KotlinStart(); //Kotlinを起動し、アドバタイズを始める
    _selfId = _signaling?.getSelfId();


    List<int> list = <int>[];
    Uint8List list8 = Uint8List(0);
    _signaling?.onDataChannelMessage =
        (_, dc, RTCDataChannelMessage data) async {
      if (data.isBinary) {
        if (list.isEmpty) {
          list = data.binary.toList();
        } else {
          list = list + data.binary.toList();
        }
      } else if (data.text == 'finish') {
        list8 = Uint8List.fromList(list);
        _viewImage2(list8);
        await _dataChannel?.send(RTCDataChannelMessage('ok'));
        bool answer = await showDialog(
            context: context,
            builder: (_) {
              return AlertDialogSample();
            });
        if (answer) {
          await _saveImage2(list8);
        }
        list.clear();
        list8.removeRange(0, list8.length);
      } else {
        await showDialog(
            context: context,
            builder: (_) {
              return AlertDialogConfirm();
            });
      }
    };

    _signaling?.onDataChannel = (_, channel) {
      _dataChannel = channel;
    };

    _signaling?.onSignalingStateChange = (SignalingState state) {
      switch (state) {
        case SignalingState.ConnectionClosed:
        case SignalingState.ConnectionError:
        case SignalingState.ConnectionOpen:
          break;
      }
    };

    _signaling?.onCallStateChange = (Session session, CallState state) {
      switch (state) {
        case CallState.CallStateNew:
          {
            setState(() {
              _session = session;
              _inCalling = true;
            });
            log('callState:new');
          }
          break;
        case CallState.CallStateBye:
          {
            setState(() {
              _inCalling = false;
            });
            _timer?.cancel();
            _dataChannel = null;
            _inCalling = false;
            _session = null;
            _text = '';
            break;
          }
        case CallState.CallStateInvite:
        case CallState.CallStateConnected:
        case CallState.CallStateRinging:
      }
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('WebRTC Drop BLE'),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: null,
            tooltip: 'setup',
          ),
        ],
      ),
      floatingActionButton: _inCalling
          ? FloatingActionButton(
              onPressed: _hangUp,
              tooltip: 'Hangup',
              child: Icon(Icons.call_end),
            )
          : FloatingActionButton(
              onPressed: () => _signaling?.Scan(),
              child: const Icon(
                Icons.search,
                size: 40,
              )),
      body: _inCalling
          ? Center(
              //
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Container(
                      width: 300,
                      height: 300,
                      child: _image != null
                          ? _image
                          : Container(
                              color: Colors.grey,
                              child: Center(
                                child: Text(
                                  'Send Image',
                                  style: TextStyle(
                                    fontSize: 20,
                                  ),
                                ),
                              ),
                            )),
                  SizedBox(
                    height: 15,
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                          child: ElevatedButton(
                            onPressed: () async {
                              Image? image = await getPictureImage_mobile();
                              _uploadPicture(image);
                            },
                            child: Text(
                              "Select",
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            style: ElevatedButton.styleFrom(
                              primary: Colors.orange,
                              onPrimary: Colors.white,
                            ),
                          ),
                          height: 60,
                          width: 140),
                      SizedBox(
                        width: 20,
                      ),
                      SizedBox(
                          child: ElevatedButton(
                            onPressed: () async {
                              _sendBinary();
                            },
                            child: Text(
                              "Send",
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            style: ElevatedButton.styleFrom(
                              primary: Colors.red[400],
                              onPrimary: Colors.white,
                            ),
                          ),
                          height: 60,
                          width: 140),
                    ],
                  ),
                  SizedBox(
                    height: 15,
                  ),
                  Container(
                      width: 200,
                      height: 200,
                      child: _image2 != null
                          ? _image2
                          : Container(
                              color: Colors.grey,
                              child: Center(
                                child: Text(
                                  'Received Image',
                                  style: TextStyle(
                                    fontSize: 20,
                                  ),
                                ),
                              ),
                            )),
                ],
              ),
              //
            )
          : ListView.builder(
              padding: const EdgeInsets.all(8),
              itemCount: list.length, //List(List名).length
              itemBuilder: (BuildContext context, int index) {
                return ListBody(
                  children: [
                    ListTile(
                        title: Text(
                          list[index].name,
                          style: TextStyle(
                              fontSize: 20, fontWeight: FontWeight.bold),
                        ),
                        subtitle: Text(list[index].address),
                        trailing: Icon(Icons.sms),
                        onTap: () {
                          setIndex(index);
                          _connect(context, list[index].address);
                          _signaling?.createIdOffer(DeviceInfo.label);
                        }),
                    Divider()
                  ],
                );
              }),
    );
  }
}

class AlertDialogSample extends StatelessWidget {
  const AlertDialogSample({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
      ),
      title: Text('Image received!'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('Do you want to save?'),
          SizedBox(
            height: 20,
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 50,
                width: 100,
                child: TextButton(
                    onPressed: () {
                      Navigator.of(context).pop(false);
                    },
                    child: Text(
                      'ignore',
                      style: TextStyle(
                        fontSize: 20,
                      ),
                    )),
              ),
              SizedBox(
                height: 50,
                width: 100,
                child: TextButton(
                    onPressed: () {
                      Navigator.of(context).pop(true);
                    },
                    child: Text(
                      'save',
                      style: TextStyle(
                        fontSize: 20,
                      ),
                    )),
              ),
            ],
          )
        ],
      ),
    );
  }
}

class AlertDialogConfirm extends StatelessWidget {
  const AlertDialogConfirm({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
      ),
      title: Text('Your Image has been sent!'),
      actions: [
        TextButton(
            onPressed: () {
              Navigator.pop(context);
            },
            child: Text(
              'ok',
              style: TextStyle(
                fontSize: 15,
              ),
            )),
      ],
    );
  }
}

class PeerConfirmDialog extends StatelessWidget {
  const PeerConfirmDialog(this.remotePeerName, {Key? key}) : super(key: key);
  final String remotePeerName;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
      ),
      title: Text('Invited from ' + remotePeerName),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('Do you accept?'),
          SizedBox(
            height: 20,
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 50,
                width: 100,
                child: TextButton(
                    onPressed: () {
                      Navigator.of(context).pop(false);
                    },
                    child: Text(
                      'Ignore',
                      style: TextStyle(
                        fontSize: 20,
                      ),
                    )),
              ),
              SizedBox(
                height: 50,
                width: 100,
                child: TextButton(
                    onPressed: () {
                      Navigator.of(context).pop(true);
                    },
                    child: Text(
                      'Accept',
                      style: TextStyle(
                        fontSize: 20,
                      ),
                    )),
              ),
            ],
          )
        ],
      ),
    );
  }
}

import 'dart:io';
import 'package:flutter/material.dart';
import 'dart:core';
import 'dart:async';
import 'dart:typed_data';
import 'signaling.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_gallery_saver/image_gallery_saver.dart';

class DataChannelSample extends StatefulWidget {
  static String tag = 'call_sample';
  final String host;

  DataChannelSample({required this.host});

  @override
  _DataChannelSampleState createState() => _DataChannelSampleState();
}

class _DataChannelSampleState extends State<DataChannelSample> {
  Signaling? _signaling;
  List<dynamic> _peers = [];
  String? _selfId;
  bool _inCalling = false;
  RTCDataChannel? _dataChannel;
  Session? _session;
  Timer? _timer;
  var _text = '';

  // ignore: unused_element

  Image? _image;
  Image? _image2;
  XFile? _xfiles;

  _DataChannelSampleState();

  @override
  initState() {
    super.initState();
    _connect(context);
  }

  @override
  deactivate() {
    super.deactivate();
    _signaling?.close();
    _timer?.cancel();
  }

  void _connect(BuildContext context) async {
    _signaling ??= Signaling(widget.host, context)..connect();
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

    _signaling?.onPeersUpdate = ((event) {
      setState(() {
        _selfId = event['self'];
        _peers = event['peers'];
      });
    });
  }

  _invitePeer(context, peerId) async {
    if (peerId != _selfId) {
      _signaling?.invite(peerId, 'data', false);
    }
  }

  _hangUp() {
    _signaling?.bye(_session!.sid);
  }

  _buildRow(context, peer) {
    var self = (peer['id'] == _selfId);
    return ListBody(children: <Widget>[
      ListTile(
        title: Text(self
            ? peer['name'] + ', ID: ${peer['id']} ' + ' [Your self]'
            : peer['name'] + ', ID: ${peer['id']} '),
        onTap: () => _invitePeer(context, peer['id']),
        trailing: Icon(Icons.sms),
        subtitle: Text('[' + peer['user_agent'] + ']'),
      ),
      Divider()
    ]);
  }

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
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_selfId != null ? ' [Your ID ($_selfId)] ' : ''),
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
          : null,
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
              shrinkWrap: true,
              padding: const EdgeInsets.all(0.0),
              itemCount: (_peers != null ? _peers.length : 0),
              itemBuilder: (context, i) {
                return _buildRow(context, _peers[i]);
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

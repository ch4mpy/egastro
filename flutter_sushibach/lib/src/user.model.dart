import 'dart:async';
import 'dart:collection';
import 'dart:convert';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:sushibach/src/network.service.dart';
import 'package:url_launcher/url_launcher.dart';

class UserModel extends ChangeNotifier {
  var _currentUser = User.anonymous;
  Iterable<String> _loginOptions = List.empty();
  Timer? _timer;

  final _mobileOAuth2Headers = NetworkService.mobileOAuth2Headers();

  UserModel() {
    _fetchLoginOptions();
    refresh();
  }

  User get current => User(
      realm: _currentUser.realm,
      username: _currentUser.username,
      roles: _currentUser.roles,
      manages: _currentUser.manages,
      worksFor: _currentUser.worksFor,
      exp: _currentUser.exp);

  Iterable<String> get loginOptions => UnmodifiableListView(_loginOptions);

  login(String loginUri) async {
    final response = await httpClient.get(Uri.parse(loginUri),
        headers: _mobileOAuth2Headers);
    final location = response.headers['location'] ?? '';
    if (location.isNotEmpty) {
      final uri = Uri.parse(location);
      if (kIsWeb) {
        await launchUrl(uri, mode: LaunchMode.platformDefault);
      } else {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    }
    refresh();
  }

  logout() async {
    final clientResponse = await httpClient.post(Uri.parse('$bffUri/logout'),
        headers: _mobileOAuth2Headers);
    if (clientResponse.statusCode >= 200 && clientResponse.statusCode < 400) {
      final location = clientResponse.headers['location'] ?? '';
      if (location.isNotEmpty) {
        await launchUrl(Uri.parse(location),
            mode: LaunchMode.externalApplication);
      }
    }
    _currentUser = User.anonymous;
    notifyListeners();
  }

  void refresh() async {
    _timer?.cancel();
    final response = await httpClient.get(Uri.parse('$bffUri/bff/v1/users/me'));
    final previousUser = _currentUser.username;
    if (response.statusCode == 200) {
      final now = DateTime.now().millisecondsSinceEpoch / 1000;
      final decoded = jsonDecode(response.body) as Map<String, dynamic>;

      final secondsBeforeExp = (decoded['exp'] - now).toInt();
      if (secondsBeforeExp > 2 && decoded['username'].toString().isNotEmpty) {
        final roles = (decoded['roles'] ?? []).cast<String>();
        _currentUser = User(
            exp: decoded['exp'],
            manages: (decoded['manages'] ?? []).cast<int>(),
            realm: decoded['realm'] ?? '',
            roles: roles,
            username: decoded['name'] ?? '',
            worksFor: (decoded['worksFor'] ?? []).cast<int>());
        _timer = Timer(Duration(seconds: (secondsBeforeExp * .8).toInt()),
            () => refresh());
      } else {
        _currentUser = User.anonymous;
        _timer = Timer(Duration(seconds: (10).toInt()), () => refresh());
      }
    } else {
      _currentUser = User.anonymous;
      _timer = Timer(Duration(seconds: (10).toInt()), () => refresh());
    }
    if (previousUser != _currentUser.username) {
      notifyListeners();
    }
  }

  void _fetchLoginOptions() async {
    final response = await httpClient.get(Uri.parse('$bffUri/login-options'));
    if (response.statusCode == 200) {
      final body =
          (jsonDecode(response.body) as List).cast<Map<String, dynamic>>();
      _loginOptions = body.where((Map<String, dynamic> e) => e.keys.contains('label') && e.values.contains('sushibach')).map((e) => e['href']);
    } else {
      _loginOptions = List.empty();
    }
    notifyListeners();
  }
}

class User {
  const User(
      {required this.realm,
      required this.username,
      required this.roles,
      required this.manages,
      required this.worksFor,
      required this.exp});

  final String realm;
  final String username;
  final List<String> roles;
  final List<int> manages;
  final List<int> worksFor;
  final int exp;

  static const User anonymous = User(
      realm: '', username: '', roles: [], manages: [], worksFor: [], exp: -1);

  bool isAuthenticated() {
    return username.isNotEmpty;
  }
}

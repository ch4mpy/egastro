# OIDC Training by Jérôme Wacongne - Flutter frontend

A sample Flutter application consuming a REST API through an OAuth2 BFF.

For this lab, we'll start from a new empty Flutter application. Once the app generated, move inside it and run `dart pub add url_launcher http go_router provider` to add the dependencies we need.

## 1. Add cookies management
Flutter `http` Dart package does not handle cookies transparently like a browser does. As both requests authorization and CSRF protection on our `spring-cloud-gateway` BFF rely on cookies, we need to add cookie support.

Let's write an `httpClient` singleton that proxy all calls to the Dart `http` one, to:
- read and parse cookies from server responses
- store cookies in memory for each server
- add a `Cookie` header to requests (with cookies in memory for that server)
- add a `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie (if any) to `POST`, `PUT`, `PATCH` and `DELETE` requests (this is expected by Spring's default CSRF protection mechanisms)

The 1st piece for that is a `Cookie` class:
```dart
class Cookie {
  final String? domain;
  final String authority;
  final String path;
  final bool secure;
  final String key;
  final String value;
  final DateTime? expires;

  Cookie(
      {required this.domain,
      required this.authority,
      required this.path,
      required this.secure,
      required this.key,
      required this.value,
      required this.expires});

  bool isToBeAttachedTo(Uri request) {
    if (secure && request.scheme != 'https') {
      return false;
    }
    return (domain?.isNotEmpty ?? false)
        ? request.host.endsWith(domain!)
        : _authority(request.host, request.port) == authority;
  }

  static Iterable<Cookie> fromSetCookieHeader(
      Uri requestUri, String setCookie) {
    final exploded =
        setCookie.split(';').map((e) => e.trim()).map((e) => e.split('='));
    final attributes = {
      for (var e in exploded) e[0].trim(): e.length > 1 ? e[1] : null
    };
    final path = attributes['path'] ?? '/';
    final expiresStr = attributes['expires'] ?? '';
    final expires = expiresStr.isNotEmpty ? DateTime.parse(expiresStr) : null;
    return attributes.entries
        .where((e) => !['EXPIRES', 'PATH', 'SECURE', 'HTTPONLY', 'SAMESITE']
            .contains(e.key.toUpperCase()))
        .map((e) => Cookie(
            domain: attributes['domain'],
            authority: _authority(requestUri.host, requestUri.port),
            path: path,
            secure: 'https' == requestUri.scheme,
            key: e.key,
            value: e.value ?? '',
            expires: expires));
  }

  static _authority(String host, int port) {
    return '$host:$port';
  }
}
```
Then, the `http` wrapper itself:
```dart
class NetworkService {
  /// An index of cookies by server ID (combination of host and port)
  final List<Cookie> _cookies = [];

  /*------------------*/
  /* Public interface */
  /*------------------*/
  Future<http.Response> head(Uri uri, {Map<String, String>? headers}) {
    return http
        .head(uri, headers: _headersWithCookies(uri, headers))
        .then((r) => _updateStoredCookies(uri, r));
  }

  Future<http.Response> get(Uri uri, {Map<String, String>? headers}) {
    return http
        .get(uri, headers: _headersWithCookies(uri, headers))
        .then((r) => _updateStoredCookies(uri, r));
  }

  Future<http.Response> post(Uri uri,
      {Map<String, String>? headers, Object? body, Encoding? encoding}) {
    return http
        .post(uri,
            headers: _headersWithCsrf(uri, _headersWithCookies(uri, headers)),
            body: body,
            encoding: encoding)
        .then((r) => _updateStoredCookies(uri, r));
  }

  Future<http.Response> put(Uri uri, {headers, body, encoding}) {
    return http
        .put(uri,
            headers: _headersWithCsrf(uri, _headersWithCookies(uri, headers)),
            body: body,
            encoding: encoding)
        .then((r) => _updateStoredCookies(uri, r));
  }

  Future<http.Response> patch(Uri uri, {headers, body, encoding}) {
    return http
        .patch(uri,
            headers: _headersWithCsrf(uri, _headersWithCookies(uri, headers)),
            body: body,
            encoding: encoding)
        .then((r) => _updateStoredCookies(uri, r));
  }

  Future<http.Response> delete(Uri uri, {headers, body, encoding}) {
    return http
        .delete(uri,
            headers: _headersWithCsrf(uri, _headersWithCookies(uri, headers)),
            body: body,
            encoding: encoding)
        .then((r) => _updateStoredCookies(uri, r));
  }

  /// Set a header to ask the BFF to answer in the 2xx range instead of a 302
  static Map<String, String> mobileOAuth2Headers(
      {Map<String, String>? headers}) {
    final mobileOAuth2Headers = headers ?? <String, String>{};
    mobileOAuth2Headers['X-RESPONSE-STATUS'] = 'NO_CONTENT';
    return mobileOAuth2Headers;
  }

  /*-----------*/
  /* Internals */
  /*-----------*/
  Map<String, String> _headersWithCookies(
      Uri request, Map<String, String>? headers) {
    final headersWithCookies = headers ?? <String, String>{};
    const isMobile = !kIsWeb;
    if (isMobile) {
      final now = DateTime.now();
      _cookies
          .removeWhere((element) => element.expires?.isBefore(now) ?? false);

      final domainCookies = _cookies.where((c) => c.isToBeAttachedTo(request));
      if (domainCookies.isNotEmpty) {
        headersWithCookies['Cookie'] =
            domainCookies.map((e) => '${e.key}=${e.value}').join("; ");
      }
    }

    return headersWithCookies;
  }

  Map<String, String> _headersWithCsrf(
      Uri request, Map<String, String>? headers) {
    final headersWithCsrf = headers ?? <String, String>{};
    final domainCookies = _cookies
        .where((c) => c.isToBeAttachedTo(request) && c.key == 'XSRF-TOKEN');
    if (domainCookies.isNotEmpty) {
      headersWithCsrf['X-XSRF-TOKEN'] = domainCookies.first.value;
    }
    return headersWithCsrf;
  }

  http.Response _updateStoredCookies(Uri requestUri, http.Response response) {
    final setCookie = response.headers['set-cookie'];
    if (setCookie?.isNotEmpty ?? false) {
      final cookies = Cookie.fromSetCookieHeader(requestUri, setCookie!);
      final cookieKeys = cookies.map((e) => e.key);
      _cookies.removeWhere((element) => cookieKeys.contains(element.key));
      _cookies.addAll(cookies);
    }
    return response;
  }
}
```
Note that we expose a `mobileOAuth2Headers` method to add a `X-RESPONSE-STATUS` to other headers, as expected by the custom `RedirectHandler` we configured in Spring Security for the BFF.

Last, let's define a few constants to make our life easier:
```dart
const bffScheme = 'http';
const bffHost = '192.168.1.182';
const bffPort = 7080;
const bffUri = '$bffScheme://$bffHost:$bffPort';
final httpClient = NetworkService();
```
Note the `httpClient` that we'll use as singleton instance in the rest of the app

## 2. User service
What we'll do here is pretty similar to what we did in the Vue application.

We'll use the following `User` representation:
```dart
class User {
  const User({required this.realm, required this.username, required this.roles, required this.manages, required this.worksFor, required this.exp});

  final String realm;
  final String username;
  final List<String> roles;
  final List<int> manages;
  final List<int> worksFor;
  final int exp;

  static const User anonymous = User(realm: '', username: '', roles: [], manages: [], worksFor: [], exp: -1);

  bool isAuthenticated() {
    return username.isNotEmpty;
  }
}
```
And then, we'll provide a service with methods to:
- retrieve the `login-options` proposed by the bff
- initiate an `authorization_code` flow on the BFF (`login`)
- trigger RP-Initiated Logout (`logout`)
- keep the session active (`refresh` the user data before the access expires)
```dart
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
        html.window.open(location, '_self');
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
            exp: decoded['exp'], manages: decoded['manages'] ?? [], realm: decoded['realm'], roles: roles, username: decoded['username'], worksFor: decoded['worksFor'] ?? []);
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
      _loginOptions = body.map((e) => e['href']);
    } else {
      _loginOptions = List.empty();
    }
    notifyListeners();
  }
}
```
Note oh in the mobile app friendly version of the user service, we send the `mobileOAuth2Headers` that  we defined in our `NetworkService` and how Flutter will follow the redirection to Keycloak authorization endpoint differently depending it runs in "web" mode (use current tab) or not (use system default browser).

## 3. Deep links
The last missing part is a way to intercept redirections from Keycloak (to the authorization-code callback and after logout) and process it inside our app instead of the system browser (we sent the request with the browser, so it's it having the response). For that we'll use "Deep links" (called "App links" by Google and "Universal links" by Apple).

For that on Android, we should add "intents" to the manifest file:
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />

    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />

    <data android:scheme="https" />
    <data android:host="192.168.1.182" />
    <data android:port="7080" />
    <data android:pathPrefix="/login/oauth2/code" />
</intent-filter>
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />

    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />

    <data android:scheme="https" />
    <data android:host="192.168.1.182" />
    <data android:port="7080" />
    <data android:pathPrefix="/ui" />
</intent-filter>
```
With this, if the app is installed, and if the auto-verification succeeded which require additional steps out of the scope of this tutorial, then each time `https://192.168.1.182:7080/login/oauth2/code/*` or `https://192.168.1.182:7080/ui/*` has to be visited by your device, then Android will delegate the handling to your application instead of the browser.

If the auto-verification could not happen, which will probably be the case on your dev environment, you'll have to manually edit the app properties on your Android device to allow this two intents to be handled by the app.

Once the intents defined to route some URIs to our app, we should define the handling of this URI in our app. Well' do this with `GoRouter` in the `main.dart` file:
```dart
const clientId = 'sushibach';
final _router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (BuildContext context, GoRouterState state) {
        return const BffDemoHomePage();
      },
      routes: <RouteBase>[
        GoRoute(
          path: 'login/oauth2/code/$clientId',
          builder: (BuildContext context, GoRouterState state) {
            return Consumer<UserModel>(builder: (context, user, child) {
              return FutureBuilder<http.Response>(
                  future: _forwardAuthorizationCode(state.uri, user),
                  builder: (BuildContext context,
                      AsyncSnapshot<http.Response> response) {
                    return const BffDemoHomePage();
                  });
            });
          },
        ),
        GoRoute(
          path: 'ui',
          builder: (BuildContext context, GoRouterState state) {
            return const BffDemoHomePage();
          },
        ),
      ],
    ),
  ],
);
```
When redirected back to `/ui` after the `authorization_code` flow has ended or after the second half of the logout (from Keycloak), there's nothing special: we just got to our main widget.

But when keycloak sends its redirection to the BFF with the authorization-code, then we want to read the `Location` header and forward the request with our cookie enhanced `networkService` so that the tokens that the BFF will get with this code are put in the session tied to our app internal HTTP client:
```dart
Future<http.Response> _forwardAuthorizationCode(Uri uri, UserModel user) async {
  final forwardUri = Uri(
      scheme: bffScheme,
      host: bffHost,
      port: bffPort,
      path: uri.path,
      queryParameters: uri.queryParameters);

  final response = await httpClient.get(forwardUri,
      headers: NetworkService.mobileOAuth2Headers());

  user.refresh();
  return response;
}
```
As we're using the same `httpClient` as we used when calling one of the `/login-options` to initiate the flow, and because that client can handle session cookies, the session for this `httpClient` will be "authorized" on the BFF and the `TokenRelay=` filter will be able to perform its task as expected.

Of course, because we are using the router, we need to update the `MainApp`
```dart
class MainApp extends StatelessWidget {
  const MainApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      routerConfig: _router,
      title: 'BFF Demo With Flutter Frontend',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
    );
  }
}
```

## 4. UI with current user state
We probably want the current user state to be tied to the root context:
```dart
void main() {
  runApp(ChangeNotifierProvider(
    create: (context) => UserModel(),
    child: const BffDemoApp(),
  ));
}
```
Then we can define home page referenced in the routing above as follow:
```dart
class BffDemoHomePage extends StatefulWidget {
  const BffDemoHomePage({super.key});

  @override
  State<BffDemoHomePage> createState() => _BffDemoHomePageState();
}

class _BffDemoHomePageState extends State<BffDemoHomePage> {
  @override
  Widget build(BuildContext context) {
    return Consumer<UserModel>(builder: (context, user, child) {
      return Scaffold(
        appBar: AppBar(
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          title: const Text('BFF Demo With Flutter Frontend'),
          actions: user.current.isAuthenticated()
              ? [
                  FloatingActionButton(
                    onPressed: user.logout,
                    tooltip: 'Logout',
                    child: const Icon(Icons.logout),
                  )
                ]
              : user.loginOptions.map((loginOpt) => FloatingActionButton(
                    onPressed: () => user.login(loginOpt),
                    tooltip: 'Login',
                    child: const Icon(Icons.login),
                  )).toList(),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              const Text(
                'You hare: ',
              ),
              Text(
                user.current.username,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
            ],
          ),
        ), // This trailing comma makes auto-formatting nicer for build methods.
      );
    });
  }
}
```
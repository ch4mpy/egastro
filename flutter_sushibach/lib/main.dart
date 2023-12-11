import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:sushibach/bff_demo_home_page.dart';
import 'package:sushibach/src/network.service.dart';
import 'package:sushibach/src/user.model.dart';
import 'package:http/http.dart' as http;

void main() {
  runApp(ChangeNotifierProvider(
    create: (context) => UserModel(),
    child: const MainApp(),
  ));
}

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

final _router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (BuildContext context, GoRouterState state) {
        return const BffDemoHomePage();
      },
      routes: <RouteBase>[
        GoRoute(
          path: 'login/oauth2/code/egastro-bff',
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
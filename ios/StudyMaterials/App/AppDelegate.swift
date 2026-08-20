import UIKit

#if canImport(FirebaseCore)
import FirebaseCore
#endif

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if canImport(FirebaseCore)
        FirebaseApp.configure()
        #endif
        
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.backgroundColor = Theme.background
        
        let rootNav: UINavigationController
        if SessionManager.shared.isLoggedIn {
            let homeVC = HomeViewController()
            rootNav = UINavigationController(rootViewController: homeVC)
        } else {
            let regVC = RegistrationViewController()
            rootNav = UINavigationController(rootViewController: regVC)
        }
        
        rootNav.navigationBar.barTintColor = Theme.background
        rootNav.navigationBar.tintColor = Theme.primary
        rootNav.navigationBar.titleTextAttributes = [
            .foregroundColor: Theme.textPrimary,
            .font: UIFont.systemFont(ofSize: 18, weight: .bold)
        ]
        rootNav.navigationBar.isTranslucent = false
        
        window?.rootViewController = rootNav
        window?.makeKeyAndVisible()
        
        return true
    }
}

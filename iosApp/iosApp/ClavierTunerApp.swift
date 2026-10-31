import SwiftUI
import UIKit
import ClavierTuner

// The iPhone app shows the tuner's screens, which are Kotlin (the ui module); MainViewController
// (TunerEntry) builds the app around the phone's microphone, settings and lifecycle (MainViewController.kt).
struct TunerView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        TunerEntry.shared.viewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct ClavierTunerApp: App {
    var body: some Scene {
        WindowGroup {
            TunerView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
                .statusBarHidden(true)
        }
    }
}

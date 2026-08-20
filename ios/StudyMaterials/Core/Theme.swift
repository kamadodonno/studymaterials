import UIKit

struct Theme {
    // Pure Pitch Black Background for OLED / Retina Display
    static let background = UIColor(red: 0.0, green: 0.0, blue: 0.0, alpha: 1.0)
    
    // Elevated Visible Dark Container Surfaces
    static let surface = UIColor(red: 0.07, green: 0.07, blue: 0.07, alpha: 1.0) // #121212
    static let surfaceVariant = UIColor(red: 0.10, green: 0.10, blue: 0.10, alpha: 1.0) // #1A1A1A
    
    // Visible Box Outlines
    static let borderOutline = UIColor(red: 0.18, green: 0.18, blue: 0.18, alpha: 1.0).cgColor // #2E2E2E
    
    // Primary Blue Accents
    static let primary = UIColor(red: 0.38, green: 0.65, blue: 0.98, alpha: 1.0) // #60A5FA
    static let primaryContainer = UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
    static let onPrimaryContainer = UIColor(red: 0.58, green: 0.77, blue: 0.99, alpha: 1.0)
    
    // Secondary Teal
    static let secondary = UIColor(red: 0.18, green: 0.83, blue: 0.75, alpha: 1.0)
    static let secondaryContainer = UIColor(red: 0.07, green: 0.16, blue: 0.15, alpha: 1.0)
    
    // Text Colors
    static let textPrimary = UIColor(red: 0.97, green: 0.98, blue: 0.99, alpha: 1.0)
    static let textSecondary = UIColor(red: 0.63, green: 0.63, blue: 0.67, alpha: 1.0)
    static let error = UIColor(red: 0.97, green: 0.44, blue: 0.44, alpha: 1.0)
}

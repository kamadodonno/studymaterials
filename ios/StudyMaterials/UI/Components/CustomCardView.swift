import UIKit

class CustomCardView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupCard()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupCard()
    }
    
    private func setupCard() {
        backgroundColor = Theme.surface
        layer.cornerRadius = 14
        layer.borderWidth = 1.0
        layer.borderColor = Theme.borderOutline
        layer.masksToBounds = true
    }
}

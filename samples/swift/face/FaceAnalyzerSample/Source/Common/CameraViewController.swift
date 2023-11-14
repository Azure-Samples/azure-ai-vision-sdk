//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import Foundation
import SwiftUI

class CameraViewController: UIViewController {
    var onViewDidLoad: (UIView) -> Void = { _ in return }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        onViewDidLoad(self.view)
    }
    
    func onViewDidLoad(perform: @escaping (UIView) -> Void) -> Self {
        onViewDidLoad = perform
        return self
    }
}

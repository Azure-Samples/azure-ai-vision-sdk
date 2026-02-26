//
// Copyright (c) Microsoft. All rights reserved.
//

import SwiftUI
import AzureAIVisionFaceUI

struct MicrosoftLogoView: View {
    var body: some View {
        HStack {
            Spacer()
            
            HStack(spacing: 8) {
                Image("MicrosoftLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(height: 20)

                Text(NSLocalizedString("AZAIF_PoweredByMicrosoft", comment: "SDK Microsoft branding text"))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
        .padding(.bottom, 12)
        .padding(.horizontal, 16)
    }
}

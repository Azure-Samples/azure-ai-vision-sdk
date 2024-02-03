//
// Copyright (c) Microsoft. All rights reserved.
//

import Foundation
import SwiftUI

struct ResultView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData

    var body: some View {
        ZStack {
            Spacer()
            Rectangle()
                .fill(Color.white)
                .edgesIgnoringSafeArea(.all)
            Spacer()
            VStack() {
                Spacer()
                Text(sessionData.resultMessage)
                    .fixedSize(horizontal: false, vertical: false)
                    .frame(height: UIFont.labelFontSize * 6, alignment: .topLeading)
                    .font(.system(size: 24))
                    .lineLimit(nil)
                    .foregroundColor(Color.black)
                    .padding(.top, -2.5)
                Spacer()
                Button(action: doneReviewInDemo, label: {
                    Text("Continue")
                        .padding()
                })
            }

        }
    }

    func doneReviewInDemo() {
        withAnimation {
            pageSelection.current = .launch
        }
    }
}

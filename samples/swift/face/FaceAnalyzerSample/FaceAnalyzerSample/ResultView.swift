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
            Rectangle()
                .fill(Color.white)
                .edgesIgnoringSafeArea(.all)

            VStack() {
                Spacer()
                Text(sessionData.resultMessage)
                    .fixedSize(horizontal: false, vertical: false)
                    .frame(height: UIFont.labelFontSize * 10, alignment: .topLeading)
                    .font(.system(size: 20))
                    .lineLimit(nil)
                    .foregroundColor(Color.black)
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

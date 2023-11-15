//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @State private var showCopyAlert = false

    var body: some View {
        VStack {
            TextField("Face API endpoint", text: $sessionData.endpoint)
                .font(Font.custom("Helvetica Neue", size: 15.0))
                .foregroundColor(Color.red)
                .background(Color.black)
                .textFieldStyle(RoundedBorderTextFieldStyle.init())
                .cornerRadius(12)
                .padding()
            SecureField("Face API key", text: $sessionData.key)
                .font(Font.custom("Helvetica Neue", size: 15.0))
                .foregroundColor(Color.red)
                .background(Color.black)
                .textFieldStyle(RoundedBorderTextFieldStyle.init())
                .cornerRadius(12)
                .padding()
            ZStack{
                TextField("Last session apim-request-id", text: $sessionData.resultId)
                                .font(Font.custom("Helvetica Neue", size: 15.0))
                                .foregroundColor(Color.red)
                                .background(Color.black)
                                .textFieldStyle(RoundedBorderTextFieldStyle.init())
                                .cornerRadius(12)
                                .disabled(true)
                                .padding()
            }
            .onTapGesture {
                if($sessionData.resultId.wrappedValue != "")
                {
                    UIPasteboard.general.string = $sessionData.resultId.wrappedValue
                    showCopyAlert = true
                }
            }
            .alert(isPresented:$showCopyAlert)
            {
                Alert(title: Text("Result ID Copied"), message: Text("Result ID has been copied"))
            }

            Spacer()
            Button(action: doneClicked){
                Text("Done")
                    .font(Font.custom("Helvetica Neue", size: 18.0))
                    .padding(16)
                    .foregroundColor(Color.white)
                    .background(Color.blue)
                    .cornerRadius(12)
            }
        }
    }
    func doneClicked() {
        saveDataToFile(sessionData: sessionData)
        withAnimation {
            pageSelection.current = .launch
        }
    }
}

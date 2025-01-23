//
// Copyright (c) Microsoft. All rights reserved.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    @State private var showCopyAlert = false

    var body: some View {
        VStack {
            TextField("Face API endpoint", text: $sessionData.endpoint)
                .autocorrectionDisabled()
                .font(Font.custom("Helvetica Neue", size: 15.0))
                .textFieldStyle(RoundedBorderTextFieldStyle.init())
                .padding(.horizontal)
                .accessibilityIdentifier("faceAPIEndpoint")
            SecureInputView("Face API key", text: $sessionData.key)
                .font(Font.custom("Helvetica Neue", size: 15.0))
                .textFieldStyle(RoundedBorderTextFieldStyle.init())
                .padding(.horizontal)
                .accessibilityIdentifier("faceAPIKey")
            ZStack {
                TextField("Last session apim-request-id", text: $sessionData.resultId)
                    .font(Font.custom("Helvetica Neue", size: 15.0))
                    .textFieldStyle(RoundedBorderTextFieldStyle.init())
                    .disabled(true)
                    .padding()
            }
                .contentShape(Rectangle())
                .onTapGesture {
                    if ($sessionData.resultId.wrappedValue != "") {
                        UIPasteboard.general.string = $sessionData.resultId.wrappedValue
                        showCopyAlert = true
                    }
                }
                .alert(isPresented: $showCopyAlert) {
                    Alert(title: Text("Result ID Copied"), message: Text("Result ID has been copied"))
                }
            Picker("Liveness mode", selection: $sessionData.livenessMode) {
                Text("Passive")
                    .tag(LivenessMode.passive)
                    .accessibilityIdentifier( "passiveMode")
                Text("Passive/Active")
                    .tag(LivenessMode.passiveActive)
                    .accessibilityIdentifier( "passiveActiveMode")
            }.pickerStyle(.segmented)
                .padding(.horizontal)
            Spacer()
            Button(action: doneClicked){
                Text("Done")
                    .font(Font.custom("Helvetica Neue", size: 18.0))
                    .padding(16)
                    .foregroundColor(Color.white)
                    .background(Color.blue)
                    .cornerRadius(12)
            }.accessibilityIdentifier( "doneButton")
        }
    }
    func doneClicked() {
        saveDataToFile(sessionData: sessionData)
        withAnimation {
            pageSelection.current = .launch
        }
    }
}

//
// Copyright (c) Microsoft. All rights reserved.
//

import SwiftUI

struct ImageSelectionView: View {
    @EnvironmentObject var pageSelection: PageSelection
    @EnvironmentObject var sessionData: SessionData
    
    @State var selectedImageData: Data? = nil
    @State var selectedImage = UIImage()

    var body: some View {
        VStack {
            Button(action: {
                sessionData.isShowPhotoLibraryForReferenceImage = true
            }) {
                HStack {
                    Image(systemName: "photo")
                        .font(.system(size: 15))
                    
                    Text("Select Reference Image")
                        .font(.headline)
                }
                .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: 40)
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(20)
                .padding(.horizontal)
            }
            
            Image(uiImage: selectedImage)
                .resizable()
                .scaledToFill()
                .frame(minWidth: 0, maxWidth: 200)
                .aspectRatio(contentMode: .fit)
                .padding()

            Button(action: nextClicked, label: {
                Text("Next")
                    .font(Font.custom("Helvetica Neue", size: 18.0))
                    .padding(16)
                    .foregroundColor(Color.white)
                    .background(Color.blue)
                    .cornerRadius(12)
            })
            .disabled(selectedImageData == nil)
        }
        .sheet(isPresented: $sessionData.isShowPhotoLibraryForReferenceImage) {
            ImagePicker(imageData: $selectedImageData, selectedImage: $selectedImage)
        }
    }

    func nextClicked() {
        if (selectedImageData != nil) {
            sessionData.referenceImageData = selectedImageData
        }
        else {
            return
        }
        withAnimation {
            pageSelection.current = .clientStart
            if let auth = obtainToken(usingEndpoint: sessionData.endpoint,
                                      key: sessionData.key,
                                      withVerify: true,
                                      verifyImage: sessionData.referenceImageData,
                                      livenessOperationMode: sessionData.livenessMode.livenessOperationMode) {
                sessionData.token = auth.token
                sessionData.sessionId = auth.id
            }
        }
    }
}

struct ImageSelectionView_Previews: PreviewProvider {
    static var previews: some View {
        ImageSelectionView()
    }
}

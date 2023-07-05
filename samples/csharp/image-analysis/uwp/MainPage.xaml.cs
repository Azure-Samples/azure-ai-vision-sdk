//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
using Azure;
using Azure.AI.Vision.Common;
using Azure.AI.Vision.ImageAnalysis;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Media;
using Windows.Storage.Pickers;
using Windows.Storage;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;


namespace MicrosoftAzureAIVisionSDKSamples.UwpImageAnalysisSample
{
    public sealed partial class MainPage : Page
    {
        public MainPage()
        {
            this.InitializeComponent();

            // Read default values for computer vision key & endpoint from environment variables (if exist).
            // If the do not exist, the user needs to manually enter them in the UI.
            try
            {
                string key = Environment.GetEnvironmentVariable(EnvironmentVariableKey);
                if (key != null && IsValidKey(key))
                {
                    keyTextBox.Text = key;
                }
            }
            catch
            {
                // Ignore
            }

            try
            {
                string endpoint = Environment.GetEnvironmentVariable(EnvironmentVariableEndpoint);
                if (endpoint != null && IsValidEndpoint(endpoint))
                {
                    endpointTextBox.Text = endpoint;
                }
            }
            catch
            {
                // Ignore
            }

            // Input image defaults
            imageUrlRadioButton.IsChecked = true;
            imageUrlTextBox.Text = "https://aka.ms/azai/vision/image-analysis-sample.jpg";
            imageFileTextBox.Text = "sample.jpg";

            // Analysis options defaults
            captionCheckBox.IsChecked = true;
            denseCaptionsCheckBox.IsChecked = true;
            objectsCheckBox.IsChecked = true;
            tagsCheckBox.IsChecked = true;
            textCheckBox.IsChecked = true;
            peopleCheckBox.IsChecked = true;
            cropSuggestionsCheckBox.IsChecked = true;
            croppingAspectRationsTextBox.Text = "0.8, 1.1";
            genderNeutralCaptionCheckBox.IsChecked = false;
        }

        private async void Analyze_ButtonClicked()
        {
            string key = keyTextBox.Text;
            if (!IsValidKey(key)) return;

            string endpoint = endpointTextBox.Text;
            if (!IsValidEndpoint(endpoint)) return;

            VisionSource visionSource = null;

            try
            {
                var serviceOptions = new VisionServiceOptions(endpoint, new AzureKeyCredential(key));

                // Create a VisionSource from the input image file or URL
                if (imageUrlRadioButton.IsChecked.HasValue && imageUrlRadioButton.IsChecked.Value)
                {
                    // Try this image URL: https://aka.ms/azai/vision/image-analysis-sample.jpg
                    if (!IsValidImageUrl()) return;
                    visionSource = VisionSource.FromUrl(new Uri(this.imageUrlTextBox.Text));
                }
                else if (imageFileRadioButton.IsChecked.HasValue && imageFileRadioButton.IsChecked.Value)
                {
                    // Try the image sample.jpg provided with this sample application
                    if (!IsValidImageFile()) return;
                    visionSource = VisionSource.FromFile(this.imageFileTextBox.Text);
                }
                else
                {
                    throw new Exception("Error: Unsupported input image option");
                }

                // Define the Image Analysis options
                var analysisOptions = new ImageAnalysisOptions();

                // Mandatory. You must set one or more features to analyze.
                // Note that 'Caption' and 'DenseCaptions' are only supported in Azure GPU regions (East US, France Central, Korea Central,
                // North Europe, Southeast Asia, West Europe, West US). Remove 'Caption' and 'DenseCaptions' from the list if your
                // Computer Vision key is not from one of those regions.
                bool featureSelected = false;
                if (captionCheckBox.IsChecked.HasValue && captionCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.Caption;
                    featureSelected = true;
                }
                if (denseCaptionsCheckBox.IsChecked.HasValue && denseCaptionsCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.DenseCaptions;
                    featureSelected = true;
                }
                if (objectsCheckBox.IsChecked.HasValue && objectsCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.Objects;
                    featureSelected = true;
                }
                if (tagsCheckBox.IsChecked.HasValue && tagsCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.Tags;
                    featureSelected = true;
                }
                if (textCheckBox.IsChecked.HasValue && textCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.Text;
                    featureSelected = true;
                }
                if (peopleCheckBox.IsChecked.HasValue && peopleCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.People;
                    featureSelected = true;
                }
                if (cropSuggestionsCheckBox.IsChecked.HasValue && cropSuggestionsCheckBox.IsChecked.Value)
                {
                    analysisOptions.Features |= ImageAnalysisFeature.CropSuggestions;
                    featureSelected = true;
                }
                if (!featureSelected)
                {
                    throw (new Exception("Error: At least one visual feature must be selected"));
                }

                // Optional. Default is "en" for English. See https://aka.ms/cv-languages for a list of supported
                // language codes and which visual features are supported for each language.
                analysisOptions.Language = ((ComboBoxItem)LanguageComboBox.SelectedItem).Tag.ToString();

                // Optional, and only relevant when you select ImageAnalysisFeature.Caption.
                // Set this to "true" to get a gender neutral caption (the default is "false").
                if (genderNeutralCaptionCheckBox.IsChecked.HasValue)
                {
                    analysisOptions.GenderNeutralCaption = genderNeutralCaptionCheckBox.IsChecked.Value;
                }

                // Optional. The only value supported now is "latest".
                analysisOptions.ModelVersion = ((ComboBoxItem)ModelVersionComboBox.SelectedItem).Tag.ToString();

                // Optional, and only relevant when you select ImageAnalysisFeature.CropSuggestions.
                // Define one or more aspect ratios for the desired cropping. Each aspect ratio needs to be in the range [0.75, 1.8].
                // If you do not set this, the service will return one crop suggestion with the aspect ratio it sees fit.
                if (!String.IsNullOrWhiteSpace(this.croppingAspectRationsTextBox.Text))
                {
                    analysisOptions.CroppingAspectRatios = StringOfNumbersToList(this.croppingAspectRationsTextBox.Text);
                }

                // Create an ImageAnalyzer object with the selected authentication credentials,
                // selected input image, and analysis options.
                using (var analyzer = new ImageAnalyzer(serviceOptions, visionSource, analysisOptions))
                {
                    NotifyUser("Please wait for image analysis results...", NotifyType.StatusMessage);

                    // Implement the Analyzed event to display results or error message
                    analyzer.Analyzed += (sender, eventArgs) =>
                    {
                        ImageAnalysisResult result = eventArgs.Result;

                        if (result.Reason == ImageAnalysisResultReason.Analyzed)
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.AppendLine($" Image height = {result.ImageHeight}");
                            sb.AppendLine($" Image width = {result.ImageWidth}");
                            sb.AppendLine($" Model version = {result.ModelVersion}");

                            if (result.Caption != null)
                            {
                                sb.AppendLine(" Caption:");
                                sb.AppendLine($"   \"{result.Caption.Content}\", Confidence {result.Caption.Confidence:0.0000}");
                            }

                            if (result.DenseCaptions != null)
                            {
                                sb.AppendLine(" Dense Captions:");
                                foreach (var caption in result.DenseCaptions)
                                {
                                    sb.AppendLine($"   \"{caption.Content}\", Bounding box {caption.BoundingBox}, Confidence {caption.Confidence:0.0000}");
                                }
                            }

                            if (result.Objects != null)
                            {
                                sb.AppendLine(" Objects:");
                                foreach (var detectedObject in result.Objects)
                                {
                                    sb.AppendLine($"   \"{detectedObject.Name}\", Bounding box {detectedObject.BoundingBox}, Confidence {detectedObject.Confidence:0.0000}");
                                }
                            }

                            if (result.Tags != null)
                            {
                                sb.AppendLine($" Tags:");
                                foreach (var tag in result.Tags)
                                {
                                    sb.AppendLine($"   \"{tag.Name}\", Confidence {tag.Confidence:0.0000}");
                                }
                            }

                            if (result.People != null)
                            {
                                sb.AppendLine($" People:");
                                foreach (var person in result.People)
                                {
                                    sb.AppendLine($"   Bounding box {person.BoundingBox}, Confidence {person.Confidence:0.0000}");
                                }
                            }

                            if (result.CropSuggestions != null)
                            {
                                sb.AppendLine($" Crop Suggestions:");
                                foreach (var cropSuggestion in result.CropSuggestions)
                                {
                                    sb.AppendLine($"   Aspect ratio {cropSuggestion.AspectRatio}: "
                                        + $"Crop suggestion {cropSuggestion.BoundingBox}");
                                };
                            }

                            if (result.Text != null)
                            {
                                sb.AppendLine($" Text:");
                                foreach (var line in result.Text.Lines)
                                {
                                    string pointsToString = "{" + string.Join(',', line.BoundingPolygon.Select(p => p.ToString())) + "}";
                                    sb.AppendLine($"   Line: '{line.Content}', Bounding polygon {pointsToString}");

                                    foreach (var word in line.Words)
                                    {
                                        pointsToString = "{" + string.Join(',', word.BoundingPolygon.Select(p => p.ToString())) + "}";
                                        sb.AppendLine($"     Word: '{word.Content}', Bounding polygon {pointsToString}, Confidence {word.Confidence:0.0000}");
                                    }
                                }
                            }

                            var resultDetails = ImageAnalysisResultDetails.FromResult(result);
                            sb.AppendLine($" Result details:");
                            sb.AppendLine($"   Image ID = {resultDetails.ImageId}");
                            sb.AppendLine($"   Result ID = {resultDetails.ResultId}");
                            sb.AppendLine($"   Connection URL = {resultDetails.ConnectionUrl}");
                            sb.AppendLine($"   JSON result = {resultDetails.JsonResult}");
                            NotifyUser(sb.ToString(), NotifyType.StatusMessage);
                        }
                        else // result.Reason == ImageAnalysisResultReason.Error
                        {
                            var errorDetails = ImageAnalysisErrorDetails.FromResult(result);
                            StringBuilder sb = new StringBuilder();
                            sb.AppendLine(" Analysis failed.");
                            sb.AppendLine($"   Error reason : {errorDetails.Reason}");
                            sb.AppendLine($"   Error code : {errorDetails.ErrorCode}");
                            sb.AppendLine($"   Error message: {errorDetails.Message}");
                            NotifyUser(sb.ToString(), NotifyType.ErrorMessage);
                        }
                    };

                    // This call does the REST call to the Image Analysis service
                    await analyzer.AnalyzeAsync();
                }
            }
            catch (Exception e)
            {
                NotifyUser(e.ToString(), NotifyType.ErrorMessage);
            }
            finally
            {
                if (visionSource != null)
                {
                    visionSource.Dispose();
                }
            }
        }

        private async void ImageFilePicker_ButtonClicked()
        {
            try
            {
                FileOpenPicker openPicker = new FileOpenPicker();
                openPicker.ViewMode = PickerViewMode.Thumbnail;
                openPicker.FileTypeFilter.Add("*");
                openPicker.SuggestedStartLocation = PickerLocationId.ComputerFolder;

                StorageFile file = await openPicker.PickSingleFileAsync();
                if (file != null)
                {
                    imageFileTextBox.Text = file.Path;
                }
            }
            catch (Exception e)
            {
                NotifyUser(e.ToString(), NotifyType.ErrorMessage);
            }
        }

        // These are the names of the environment variables where default values
        // for computer vision key & endpoint can be stored
        private static readonly string EnvironmentVariableKey = "VISION_KEY";
        private static readonly string EnvironmentVariableEndpoint = "VISION_ENDPOINT";

        // Validates the format of the Computer Vision Key.
        // Returns true if the key is valid, false otherwise.
        private bool IsValidKey(string key)
        {
            if (String.IsNullOrWhiteSpace(key))
            {
                NotifyUser("Error: Missing computer vision key.", NotifyType.ErrorMessage);
                return false;
            }

            if (!Regex.IsMatch(key, @"^[a-fA-F0-9]{32}$"))
            {
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("Error: Invalid value for computer vision key: " + key);
                sb.AppendLine("It should be a 32-character HEX number, no dashes.");
                NotifyUser(sb.ToString(), NotifyType.ErrorMessage);
                return false;
            }

            NotifyUser(" ", NotifyType.StatusMessage);
            return true;
        }

        // Validates the format of the Computer Vision Endpoint URL.
        // Returns true if the endpoint is valid, false otherwise.
        private bool IsValidEndpoint(string endpoint)
        {
            if (String.IsNullOrWhiteSpace(endpoint))
            {
                NotifyUser("Error: Missing computer vision endpoint.", NotifyType.ErrorMessage);
                return false;
            }

            if (!Regex.IsMatch(endpoint, @"^https://\S+\.cognitiveservices\.azure\.com/?$"))
            {
                NotifyUser("Error: Invalid value for computer vision endpoint: " + endpoint, NotifyType.ErrorMessage);
                return false;
            }

            NotifyUser(" ", NotifyType.StatusMessage);
            return true;
        }

        // Validates the format of the input image file.
        // Returns true if the endpoint is valid, false otherwise.
        private bool IsValidImageFile()
        {
            string imageFile = this.imageFileTextBox.Text;

            if (String.IsNullOrWhiteSpace(imageFile))
            {
                NotifyUser("Error: Missing input image file name.", NotifyType.ErrorMessage);
                return false;
            }

            NotifyUser(" ", NotifyType.StatusMessage);
            return true;
        }

        // Validates the format of the input image URL.
        // Returns true if the endpoint is valid, false otherwise.
        private bool IsValidImageUrl()
        {
            string imageUrl = this.imageUrlTextBox.Text;

            if (String.IsNullOrWhiteSpace(imageUrl))
            {
                NotifyUser("Error: Missing input image URL.", NotifyType.ErrorMessage);
                return false;
            }

            if (!Uri.IsWellFormedUriString(imageUrl, UriKind.Absolute))
            {
                NotifyUser("Error: Malformed input image URL " + imageUrl, NotifyType.ErrorMessage);
                return false;
            }

            NotifyUser(" ", NotifyType.StatusMessage);
            return true;
        }

        // This method takes a string that contains one or more comma-separated
        // numbers, and returns the numbers as a List<double> type.
        private static List<double> StringOfNumbersToList(string input)
        {
            List<double> result = new List<double>();
            string[] numbers = input.Split(',');
            foreach (string number in numbers)
            {
                if (double.TryParse(number, out double parsedNumber))
                {
                    result.Add(parsedNumber);
                }
            }
            return result;
        }

        private enum NotifyType
        {
            StatusMessage,
            ErrorMessage
        };

        // Display a message to the user.
        // This method may be called from any thread.
        private void NotifyUser(string strMessage, NotifyType type)
        {
            // If called from the UI thread, then update immediately.
            // Otherwise, schedule a task on the UI thread to perform the update.
            if (Dispatcher.HasThreadAccess)
            {
                UpdateStatus(strMessage, type);
            }
            else
            {
                var task = Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () => UpdateStatus(strMessage, type));
            }
        }

        private void UpdateStatus(string strMessage, NotifyType type)
        {
            switch (type)
            {
                case NotifyType.StatusMessage:
                    statusBorder.Background = new SolidColorBrush(Windows.UI.Colors.Green);
                    break;
                case NotifyType.ErrorMessage:
                    statusBorder.Background = new SolidColorBrush(Windows.UI.Colors.Red);
                    break;
            }

            statusBlock.Text = strMessage;
            // Collapse the StatusBlock if it has no text to conserve real estate.
            statusBorder.Visibility = (statusBlock.Text != String.Empty) ? Visibility.Visible : Visibility.Collapsed;
            if (statusBlock.Text != String.Empty)
            {
                statusBorder.Visibility = Visibility.Visible;
                statusPanel.Visibility = Visibility.Visible;
            }
            else
            {
                statusBorder.Visibility = Visibility.Collapsed;
                statusPanel.Visibility = Visibility.Collapsed;
            }
            // Raise an event if necessary to enable a screen reader to announce the status update.
            var peer = Windows.UI.Xaml.Automation.Peers.FrameworkElementAutomationPeer.FromElement(statusBlock);
            if (peer != null)
            {
                peer.RaiseAutomationEvent(Windows.UI.Xaml.Automation.Peers.AutomationEvents.LiveRegionChanged);
            }
        }
    }
}

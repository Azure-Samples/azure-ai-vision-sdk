# Cognitive Services Computer Vision Service
Use this interface to perform operations for Computer Vision Service.

1. Start by creating an Azure Cognitive Services resource, specifically a Computer Vision resource. For instructions, see [Create a Cognitive Services resource using the portal](https://docs.microsoft.com/en-us/azure/cognitive-services/cognitive-services-apis-create-account).
2. From the Azure Portal, copy the key and endpoint required to make the call. For instructions, see [Get the keys for your resource](https://docs.microsoft.com/en-us/azure/cognitive-services/cognitive-services-apis-create-account#get-the-keys-for-your-resource).
3. Create the request URL by concatenating the endpoint url to the operation API. For instance, concatenate the endpoint url for <https://westus2.api.cognitive.microsoft.com> to the /vision/v4.0-preview.1/datasets API.

### Authentication ###
Include the following header when making a call to any API in this document.

- Ocp-Apim-Subscription-Key: [key from the Computer Vision resource]

## Version: 2023-02-01-preview

### /imageanalysis:analyze

#### POST
##### Summary

Analyze the input image. The request either contains image stream with any content type ['image/*', 'application/octet-stream'], or a JSON payload which includes an url property to be used to retrieve the image stream.

##### Parameters

| Name | Located in | Description | Required | Schema |
| ---- | ---------- | ----------- | -------- | ---- |
| features | query | The visual features requested: tags, adult, objects, caption, read, smartCrops, people. | No | [ string ] |
| model-name | query | Optional parameter to specify the custom trained model. | No | string |
| language | query | The desired language for output generation. If this parameter is not specified, the default value is \\"en\\". See https://aka.ms/cv-languages for a list of supported languages. | No | string |
| smartcrops-aspect-ratios | query | A list of aspect ratios to use for smart cropping. Aspect ratios are calculated by dividing the target crop width by the height. Multiple values should be comma-separated. | No | string |
| gender-neutral-caption | query | Whether gender terms should be removed from image captions. | No | boolean |
| api-version | query | Requested API version. | Yes | string |
| body | body | A JSON document with a URL pointing to the image that is to be analyzed. | Yes | [ImageUrl](#imageurl) |

##### Responses

| Code | Description | Schema |
| ---- | ----------- | ------ |
| 200 | Success | [ImageAnalysisResult](#imageanalysisresult) |
| default | Error | [ErrorResponse](#errorresponse) |

### Models

#### AdultMatch

An object describing adult content match.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| isMatch | boolean | A value indicating if the image is matched adult content. | Yes |
| confidence | double | A value indicating the confidence level of matched adult content. | Yes |

#### AdultResult

An object describing whether the image contains adult-oriented content and/or is racy.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| adult | [AdultMatch](#adultmatch) |  | Yes |
| racy | [AdultMatch](#adultmatch) |  | Yes |
| gore | [AdultMatch](#adultmatch) |  | Yes |

#### BoundingBox

A bounding box for an area inside an image.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| x | integer | Left-coordinate of the top left point of the area, in pixels. | Yes |
| y | integer | Top-coordinate of the top left point of the area, in pixels. | Yes |
| w | integer | Width measured from the top-left point of the area, in pixels. | Yes |
| h | integer | Height measured from the top-left point of the area, in pixels. | Yes |

#### BoundingRegion

Bounding box on a specific page of the input.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| pageNumber | integer | 1-based page number of page containing the bounding region. | Yes |
| boundingBox | [ double ] | Quadrangle bounding box, with coordinates specified relative to the top-left of the page. The eight numbers represent the four points, clockwise from the top-left corner relative to the text orientation. | Yes |

#### CaptionResult

A brief description of what the image depicts.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| text | string | The text of the caption. | Yes |
| confidence | double | The level of confidence the service has in the caption. | Yes |

#### CropRegion

A region identified for smart cropping. There will be one region returned for each requested aspect ratio.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| aspectRatio | double | The aspect ratio of the crop region. | Yes |
| boundingBox | [BoundingBox](#boundingbox) |  | Yes |

#### CurrencyObject

Currency object.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| currency | string | The currency name. | No |
| amount | double | The amount of currency. | No |

#### Description

A brief description of what the image depicts.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| text | string | The text of the caption. | Yes |
| confidence | double | The level of confidence the service has in the caption. | Yes |

#### DescriptionResult

A list of descriptions sorted by confidence level.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| values | [ [Description](#description) ] | A list of descriptions sorted by confidence level. | Yes |

#### DetectedObject

Describes a detected object in an image.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| id | string | Id of the detected object. | No |
| boundingBox | [BoundingBox](#boundingbox) |  | Yes |
| tags | [ [Tag](#tag) ] | Classification confidences of the detected object. | Yes |

#### DetectedPerson

A person detected in an image.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| boundingBox | [BoundingBox](#boundingbox) |  | Yes |
| confidence | double | Confidence score of having observed the person in the image, as a value ranging from 0 to 1. | Yes |

#### Document

An object describing the location and semantic content of a document.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| docType | string | Document type. | Yes |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the document. | Yes |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the document in the reading order concatenated content. | Yes |
| fields | object | Dictionary of named field values. | Yes |
| confidence | double | Confidence of correctly extracting the document. | Yes |

#### DocumentBlock

A block object that describes various layout elements.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| pageNumber | integer | 1-based page number in the input document. | Yes |
| boundingPoly | [ double ] | Bounding polygon of the layout block element. | Yes |

#### DocumentEntity

An object representing various categories of entities.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| category | string | Entity type. | Yes |
| subCategory | string | Entity sub type. | No |
| content | string | Entity content. | Yes |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the entity. | Yes |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the entity in the reading order concatenated content. | Yes |
| confidence | double | Confidence of correctly extracting the entity. | Yes |

#### DocumentField

An object representing the content and location of a field value.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| type | string | Data type of the field value. | Yes |
| valueString | string | String value. | No |
| valueDate | string | Date value in YYYY-MM-DD format (ISO 8601). | No |
| valueTime | string | Time value in hh:mm:ss format (ISO 8601). | No |
| valuePhoneNumber | string | Phone number value in E.164 format (ex. +19876543210). | No |
| valueNumber | double | Floating point value. | No |
| valueInteger | integer | Integer value. | No |
| valueSelectionMark | string | Selection mark value. | No |
| valueSignature | string | Presence of signature. | No |
| valueCountryRegion | string | 3-letter country code value (ISO 3166-1 alpha-3). | No |
| valueCurrency | [CurrencyObject](#currencyobject) |  | No |
| valueArray | [ [DocumentField](#documentfield) ] | Array of field values. | No |
| valueObject | object | Dictionary of named field values. | No |
| content | string | Field content. | No |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the field. | No |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the field in the reading order concatenated content. | No |
| confidence | double | Confidence of correctly extracting the field. | No |

#### DocumentKeyValueElement

An object representing the field key or value in a key-value pair.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| content | string | Concatenated content of the key-value element in reading order. | Yes |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the key-value element. | No |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the key-value element in the reading order concatenated content. | Yes |

#### DocumentKeyValuePair

An object representing a form field with distinct field label (key) and field value (may be empty).

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| key | [DocumentKeyValueElement](#documentkeyvalueelement) |  | Yes |
| value | [DocumentKeyValueElement](#documentkeyvalueelement) |  | No |
| confidence | double | Confidence of correctly extracting the key-value pair. | Yes |

#### DocumentLanguage

An object representing the detected language for a given text span.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the text elements in the concatenated content the language applies to. | Yes |
| languageCode | string | Detected language. Value may an ISO 639-1 language code (ex. "en", "fr") or BCP 47 language tag (ex. "zh-Hans"). | Yes |
| confidence | double | Confidence of correctly identifying the language. | Yes |

#### DocumentLine

A content line object consisting of an adjacent sequence of content elements, such as words and selection marks.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| content | string | Concatenated content of the contained elements in reading order. | Yes |
| boundingBox | [ double ] | Bounding box of the line. | Yes |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the line in the reading order concatenated content. | Yes |

#### DocumentPage

The content and layout elements extracted from a page from the input.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| height | double | The height of the image/PDF in pixels/inches, respectively. | Yes |
| width | double | The width of the image/PDF in pixels/inches, respectively. | Yes |
| angle | double | The general orientation of the content in clockwise direction, measured in degrees between (-180, 180]. | Yes |
| pageNumber | integer | 1-based page number in the input document. | Yes |
| words | [ [DocumentWord](#documentword) ] | Extracted words from the page. | Yes |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the page in the reading order concatenated content. | Yes |
| selectionMarks | [ [DocumentSelectionMark](#documentselectionmark) ] | Extracted selection marks from the page. | No |
| lines | [ [DocumentLine](#documentline) ] | Extracted lines from the page, potentially containing both textual and visual elements. | Yes |

#### DocumentSelectionMark

A selection mark object representing check boxes, radio buttons, and other elements indicating a selection.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| state | string | State of the selection mark. | Yes |
| boundingBox | [ double ] | Bounding box of the selection mark. | Yes |
| span | [DocumentSpan](#documentspan) |  | Yes |
| confidence | double | Confidence of correctly extracting the selection mark. | Yes |

#### DocumentSpan

Contiguous region of the concatenated content property, specified as an offset and length.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| offset | integer | Zero-based index of the content represented by the span. | Yes |
| length | integer | Number of characters in the content represented by the span. | Yes |

#### DocumentStyle

An object representing observed text styles.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| isHandwritten | boolean | Is content handwritten or not. | No |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the text elements in the concatenated content the style applies to. | Yes |
| confidence | double | Confidence of correctly identifying the style. | No |

#### DocumentTable

A table object consisting table cells arranged in a rectangular layout.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| rowCount | integer | Number of rows in the table. | Yes |
| columnCount | integer | Number of columns in the table. | Yes |
| cells | [ [DocumentTableCell](#documenttablecell) ] | Cells contained within the table. | Yes |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the table. | No |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the table in the reading order concatenated content. | Yes |

#### DocumentTableCell

An object representing the location and content of a table cell.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| kind | string | Table cell kind. | No |
| rowIndex | integer | Row index of the cell. | Yes |
| columnIndex | integer | Column index of the cell. | Yes |
| rowSpan | integer | Number of rows spanned by this cell. | No |
| columnSpan | integer | Number of columns spanned by this cell. | No |
| content | string | Concatenated content of the table cell in reading order. | Yes |
| boundingRegions | [ [BoundingRegion](#boundingregion) ] | Bounding regions covering the table cell. | Yes |
| spans | [ [DocumentSpan](#documentspan) ] | Location of the table cell in the reading order concatenated content. | Yes |

#### DocumentWord

A word object consisting of a contiguous sequence of characters. For non-space delimited languages,
such as Chinese, Japanese, and Korean, each character is represented as its own word.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| content | string | Text content of the word. | Yes |
| boundingBox | [ double ] | Bounding box of the word. | Yes |
| confidence | double | Confidence of correctly extracting the word. | Yes |
| span | [DocumentSpan](#documentspan) |  | Yes |

#### ErrorResponse

Response returned when an error occurs.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| error | [ErrorResponseDetails](#errorresponsedetails) |  | Yes |

#### ErrorResponseDetails

Error info.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| code | string | Error code. | Yes |
| message | string | Error message. | Yes |
| target | string | Target of the error. | No |
| details | [ [ErrorResponseDetails](#errorresponsedetails) ] | List of detailed errors. | No |
| innererror | [ErrorResponseInnerError](#errorresponseinnererror) |  | No |

#### ErrorResponseInnerError

Detailed error.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| code | string | Error code. | Yes |
| message | string | Error message. | Yes |
| innererror | [ErrorResponseInnerError](#errorresponseinnererror) |  | No |

#### ImageAnalysisResult

Describe the combined results of different types of image analysis.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| customModelResult | [ImagePredictionResult](#imagepredictionresult) |  | No |
| captionResult | [CaptionResult](#captionresult) |  | No |
| objectsResult | [ObjectsResult](#objectsresult) |  | No |
| modelVersion | string | Model Version. | Yes |
| metadata | [ImageMetadataApiModel](#imagemetadataapimodel) |  | Yes |
| tagsResult | [TagsResult](#tagsresult) |  | No |
| adultResult | [AdultResult](#adultresult) |  | No |
| readResult | [ReadResult](#readresult) |  | No |
| smartCropsResult | [SmartCropsResult](#smartcropsresult) |  | No |
| peopleResult | [PeopleResult](#peopleresult) |  | No |

#### ImageMetadataApiModel

The image metadata information such as height and width.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| width | integer | The width of the image in pixels. | Yes |
| height | integer | The height of the image in pixels. | Yes |

#### ImagePredictionResult

Describes the prediction result of an image.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| tagsResult | [TagsResult](#tagsresult) |  | Yes |
| objectsResult | [ObjectsResult](#objectsresult) |  | Yes |

#### ImageUrl

A JSON document with a URL pointing to the image that is to be analyzed.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| url | string | Publicly reachable URL of an image. | Yes |

#### ObjectsResult

Describes detected objects in an image.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| values | [ [DetectedObject](#detectedobject) ] | An array of detected objects. | Yes |

#### PeopleResult

An object describing whether the image contains people.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| values | [ [DetectedPerson](#detectedperson) ] | An array of detected people. | Yes |

#### ReadResult

The results of an Read operation.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| stringIndexType | string | The method used to compute string offset and length, possible values include: 'textElements', 'unicodeCodePoint', 'utf16CodeUnit' etc. | Yes |
| content | string | Concatenate string representation of all textual and visual elements in reading order. | Yes |
| pages | [ [DocumentPage](#documentpage) ] | A list of analyzed pages. | Yes |
| tables | [ [DocumentTable](#documenttable) ] | Extracted tables. | No |
| blocks | [ [DocumentBlock](#documentblock) ] | Extracted layout blocks from the page. | No |
| keyValuePairs | [ [DocumentKeyValuePair](#documentkeyvaluepair) ] | Extracted key-value pairs. | No |
| entities | [ [DocumentEntity](#documententity) ] | Extracted entities. | No |
| styles | [ [DocumentStyle](#documentstyle) ] | Extracted font styles. | No |
| documents | [ [Document](#document) ] | Extracted documents. | No |

#### SmartCropsResult

Smart cropping result.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| values | [ [CropRegion](#cropregion) ] | Recommended regions for cropping the image. | Yes |

#### Tag

An entity observation in the image, along with the confidence score.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| name | string | Name of the entity. | Yes |
| confidence | double | The level of confidence that the entity was observed. | Yes |

#### TagsResult

A list of tags with confidence level.

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| values | [ [Tag](#tag) ] | A list of tags with confidence level. | Yes |

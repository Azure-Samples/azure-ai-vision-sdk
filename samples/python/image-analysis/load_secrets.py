# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
"""
Azure AI Vision SDK -- Python Image Analysis Samples

Helper functions to load and validate secrets. These secrets should never be compiled into source code.
They should be loaded at run-time from a secure location. In these samples we first try to load
secrets from environment variables, and then again from command line arguments.
"""

import re

# These are the constant names of the environment variables you can set if you want to specify
# secrets using environment variables instead of command-line arguments
ENVIRONMENT_VARIABLE_KEY = "VISION_KEY"
ENVIRONMENT_VARIABLE_ENDPOINT = "VISION_ENDPOINT"

# These will be populated by the code below, by reading run-time arguments or environment variables.
# The sample code will need both values in order to authenticate against the Image Analysis service.
# The endpoint has the form "https://<your-computer-vision-resource-name>.cognitiveservices.azure.com".
# The key is a 32-character HEX number (no dashes), found in the Azure portal
# (for example "d0dbd4c2a933a6f18c785a526da83e15").
endpoint = None
key = None


def load_succeeded(argv):
    """
    Main function called to load and validate computer vision key & endpoint.
    Returns True on success, False on failure.
    """

    load_succeeded = load_key_succeeded(argv) and load_endpoint_succeeded(argv)

    if load_succeeded:
        # Do not print the full value of your Computer Vision key to the console (or log file).
        # Here we only log the last 3 characters of the key.
        masked_key = "*" * 29 + key[29:]
        print(" Using computer vision key: {}".format(masked_key))
        print(" Using computer vision endpoint: {}".format(endpoint))
        print()

    return load_succeeded


def load_key_succeeded(argv):
    """
    Function to load and validate computer vision key. Returns True on success, False on failure.
    """
    global key
    found_key = False

    for arg in argv:
        if arg in ["--key", "-k"]:
            key_index = argv.index(arg) + 1
            if key_index < len(argv):
                key = argv[key_index]
                found_key = True
                break

    if not found_key:
        try:
            import os
            key = os.environ[ENVIRONMENT_VARIABLE_KEY]
        except:
            pass

    return is_valid_key()


def load_endpoint_succeeded(argv):
    """
    Function to load and validate computer vision endpoint. Returns True on success, False on failure.
    """
    global endpoint
    found_endpoint = False

    for arg in argv:
        if arg in ["--endpoint", "-e"]:
            endpoint_index = argv.index(arg) + 1
            if endpoint_index < len(argv):
                endpoint = argv[endpoint_index]
                found_endpoint = True
                break

    if not found_endpoint:
        try:
            import os
            endpoint = os.environ[ENVIRONMENT_VARIABLE_ENDPOINT]
        except:
            pass

    return is_valid_endpoint()


def is_valid_key():
    """
    Validates the format of the Computer Vision Key.
    Returns True if the key is valid, False otherwise.
    """
    global key

    if key is None or len(key) == 0:
        print(" Error: Missing computer vision key.")
        print()
        return False

    if len(key) != 32 or not all(c in "0123456789abcdefABCDEF" for c in key):
        print(" Error: Invalid value for computer vision key: {}".format(key))
        print(" It should be a 32-character HEX number.")
        print()
        return False

    return True


def is_valid_endpoint():
    """
    Validates the format of the Computer Vision Endpoint URL.
    Returns True if the endpoint is valid, False otherwise.
    """
    global endpoint

    if endpoint is None or len(endpoint) == 0:
        print(" Error: Missing computer vision endpoint.")
        print()
        return False

    if re.search(r"^https://\S+\.cognitiveservices\.azure\.com/?$", endpoint):
        return True
    else:
        print(" Error: Invalid value for computer vision endpoint: {}".format(endpoint))
        print(" It should be in the form:")
        print(" https://<your-computer-vision-resource-name>.cognitiveservices.azure.com")
        print()
        return False
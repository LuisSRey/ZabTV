// AceStream official integration (MIT (c) 2019 Ace Stream, verbatim from
// acestream/acestream-engine-client).
package org.acestream.engine.service.v0;

/**
 * Callback to receive result of "startEngineWithCallback" command.
 */
oneway interface IStartEngineResponse {
    void onResult(boolean success);
}

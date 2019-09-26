const log = require("./logging");
const NativeCodePush = require("react-native").NativeModules.CodePush;

const RestartManager = (() => {
    let _allowed = true;
    let _restartInProgress = false;
    let _restartQueue = [];
    let _pathPrefix = '';

    function allow() {
        log("Re-allowing restarts");
        _allowed = true;

        if (_restartQueue.length) {
            log("Executing pending restart");
            restartApp(_restartQueue.shift(1), _pathPrefix);
        }
    }

    function clearPendingRestart() {
        _restartQueue = [];
    }

    function disallow() {
        log("Disallowing restarts");
        _allowed = false;
    }

    async function restartApp(onlyIfUpdateIsPending = false, pathPrefix) {
        _pathPrefix = pathPrefix;
        if (_restartInProgress) {
            log("Restart request queued until the current restart is completed");
            _restartQueue.push(onlyIfUpdateIsPending);
        } else if (!_allowed) {
            log("Restart request queued until restarts are re-allowed");
            _restartQueue.push(onlyIfUpdateIsPending);
        } else {
            _restartInProgress = true;
            if (await NativeCodePush.restartApp(onlyIfUpdateIsPending, pathPrefix)) {
                // The app has already restarted, so there is no need to
                // process the remaining queued restarts.
                log("Restarting app");
                return;
            }

            _restartInProgress = false;
            if (_restartQueue.length) {
                restartApp(_restartQueue.shift(1), pathPrefix);
            }
        }
    }

    return {
        allow,
        clearPendingRestart,
        disallow,
        restartApp
    };
})();

module.exports = RestartManager;

'use client';

import { useActionState } from 'react';
import { generateSession, type GenerateState } from './actions';

const initialState: GenerateState = {};

// Client form wired to the `generateSession` server action. On a validation /
// session-create failure the action returns an error message (and echoes the
// resource + mode); on success it redirects to /native/?s=<sid>.
export default function GenerateForm({ config }: { config: React.ReactNode }) {
  const [state, formAction, pending] = useActionState(generateSession, initialState);
  const resource = state.resource ?? '';
  const mode = state.mode ?? 'PassiveActive';

  return (
    <>
      {state.error ? <p className="error">{state.error}</p> : null}
      <form action={formAction}>
        <label htmlFor="resource">Face resource name</label>
        <div className="url-field">
          <span className="affix">https://</span>
          <input
            id="resource"
            name="resource"
            required
            autoComplete="off"
            placeholder="my-face-resource"
            defaultValue={resource}
          />
          <span className="affix">.cognitiveservices.azure.com</span>
        </div>

        <label htmlFor="apiKey">API key</label>
        <input
          id="apiKey"
          name="apiKey"
          type="password"
          required
          autoComplete="off"
          placeholder="Ocp-Apim-Subscription-Key"
        />

        <label htmlFor="mode">Liveness operation mode</label>
        <select id="mode" name="mode" defaultValue={mode}>
          <option value="PassiveActive">Passive-Active (default)</option>
          <option value="Passive">Passive</option>
        </select>

        <label htmlFor="verifyImage">Verify image (optional)</label>
        <input id="verifyImage" name="verifyImage" type="file" accept="image/*" />
        <p className="hint">
          Attach a reference photo to run liveness <em>with face verification</em>. Leave empty for
          liveness only.
        </p>

        <button type="submit" disabled={pending}>
          {pending ? 'Generating\u2026' : 'Generate & launch'}
        </button>
      </form>
      {config}
    </>
  );
}

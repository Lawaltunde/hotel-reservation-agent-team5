import { describe, expect, it } from "vitest";

import { buildPrompt, findEmails, followUpForCity, followUpForErrors, MAX_PROMPT_LENGTH } from "@/components/chat/intake";

describe("buildPrompt", () => {
  it("joins the user's messages oldest first, one per line", () => {
    expect(buildPrompt(["cheapest hotel in Ottawa tonight", "2 adults", "a@example.com"])).toBe(
      "cheapest hotel in Ottawa tonight\n2 adults\na@example.com",
    );
  });

  it("trims messages and drops blank ones", () => {
    expect(buildPrompt(["  Kingston  ", "", "   ", "\tfriday\t"])).toBe("Kingston\nfriday");
    expect(buildPrompt([])).toBe("");
  });

  it("keeps only the latest message's email so a correction wins", () => {
    expect(buildPrompt(["send it to old@example.com", "Ottawa tomorrow", "actually use new@example.ca"])).toBe(
      "send it to\nOttawa tomorrow\nactually use new@example.ca",
    );
  });

  it("closes the gap left by a removed address", () => {
    expect(buildPrompt(["mail old@example.com please", "use new@example.com"])).toBe("mail please\nuse new@example.com");
  });

  it("drops a message that was only an old address", () => {
    expect(buildPrompt(["old@example.com", "new@example.com"])).toBe("new@example.com");
  });

  it("leaves two addresses in the same latest message for the backend to query", () => {
    expect(buildPrompt(["Ottawa", "a@example.com or b@example.com"])).toBe("Ottawa\na@example.com or b@example.com");
  });

  it("changes nothing when no message has an email", () => {
    expect(buildPrompt(["Ottawa", "tonight  for   2"])).toBe("Ottawa\ntonight  for   2");
  });

  it("matches the backend's prompt limit", () => {
    expect(MAX_PROMPT_LENGTH).toBe(2000);
  });
});

describe("findEmails", () => {
  it("finds every distinct address, lower-cased", () => {
    expect(findEmails("A.Person@Example.com, a.person@example.com and b+trip@mail.example.ca")).toEqual([
      "a.person@example.com",
      "b+trip@mail.example.ca",
    ]);
  });

  it("ignores things that only look like addresses", () => {
    expect(findEmails("me@localhost, @example.com, user@example")).toEqual([]);
  });
});

describe("followUpForErrors", () => {
  it("asks one question when one thing is missing", () => {
    expect(
      followUpForErrors([{ field: "email", message: "Include the email address the result should be sent to" }], "Ottawa"),
    ).toEqual({ text: "Thanks. What email address should I send the results to?" });
    expect(followUpForErrors([{ field: "city", message: "City is required" }], "").text).toBe(
      "Thanks. Which city would you like to stay in?",
    );
    expect(followUpForErrors([{ field: "checkIn", message: "Check-in date is required" }], "").text).toBe(
      "Thanks. Which day would you like to check in?",
    );
  });

  it("lists several missing things in a fixed order", () => {
    const errors = [
      { field: "email", message: "Include the email address the result should be sent to" },
      { field: "checkIn", message: "Check-in date is required" },
      { field: "city", message: "City is required" },
    ];
    expect(followUpForErrors(errors, "").text).toBe(
      "Thanks. To start, I still need the city you'd like to stay in, your check-in date and the email address for the results.",
    );
    expect(followUpForErrors(errors.slice(0, 1).concat(errors[2]), "").text).toBe(
      "Thanks. To start, I still need the city you'd like to stay in and the email address for the results.",
    );
  });

  it("passes on an error about a wrong value in the backend's words", () => {
    expect(followUpForErrors([{ field: "checkIn", message: "Check-in date is in the past" }], "").text).toBe(
      "Check-in date is in the past.",
    );
    expect(followUpForErrors([{ field: "rooms", message: "At least 1 room is required" }], "").text).toBe(
      "At least 1 room is required.",
    );
  });

  it("asks for what is missing first, then repeats other problems once", () => {
    const followUp = followUpForErrors(
      [
        { field: "city", message: "City is required" },
        { field: "maxBudget", message: "budget must be positive" },
        { field: "maxBudget", message: "Budget must be positive." },
      ],
      "",
    );
    expect(followUp.text).toBe("Thanks. Which city would you like to stay in? Budget must be positive.");
    expect(followUp.choices).toBeUndefined();
  });

  it("guesses the field from the text of a field-less error", () => {
    expect(followUpForErrors([{ field: null, message: "city is required" }], "").text).toBe(
      "Thanks. Which city would you like to stay in?",
    );
    expect(followUpForErrors([{ field: null, message: "Include the e-mail address" }], "").text).toBe(
      "Thanks. What email address should I send the results to?",
    );
  });

  it("offers each address as a chip when there were several", () => {
    const followUp = followUpForErrors(
      [{ field: "email", message: "Use only one email address" }],
      "Ottawa, send to Me@Example.com or partner@example.ca",
    );
    expect(followUp.text).toBe("I found more than one email address. Which one should I send the results to?");
    expect(followUp.choices).toEqual([
      { label: "Use me@example.com", reply: "me@example.com" },
      { label: "Use partner@example.ca", reply: "partner@example.ca" },
    ]);
  });

  it("falls back to a generic question when nothing is usable", () => {
    const generic = "Something in that request didn't work. Could you say it another way?";
    expect(followUpForErrors([], "").text).toBe(generic);
    expect(followUpForErrors([{ field: null, message: "   " }], "").text).toBe(generic);
  });
});

describe("followUpForCity", () => {
  it("offers the suggested cities as chips", () => {
    expect(followUpForCity("'London' matches several Canadian places", ["London, ON", "New London, PE"])).toEqual({
      text: "Which Canadian city did you mean? 'London' matches several Canadian places.",
      choices: [
        { label: "Search London, ON", reply: "Search London, ON" },
        { label: "Search New London, PE", reply: "Search New London, PE" },
      ],
    });
  });

  it("asks for a Canadian city when there is nothing to suggest", () => {
    expect(followUpForCity("'Paris' is not in Canada", [])).toEqual({
      text: "I can only search hotels in Canada. 'Paris' is not in Canada. Which Canadian city should I search?",
    });
    expect(followUpForCity(undefined, []).text).toBe("I can only search hotels in Canada. Which Canadian city should I search?");
    expect(followUpForCity(undefined, ["Ottawa"]).text).toBe("Which Canadian city did you mean?");
  });
});

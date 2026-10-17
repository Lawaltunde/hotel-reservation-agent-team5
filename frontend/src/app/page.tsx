import { BackendStatus } from "@/components/BackendStatus";

export default function HomePage() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-10 px-4 py-12 sm:px-6 sm:py-16">
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">Hotel Deal Agent</h1>
          <BackendStatus />
        </div>
        <p className="text-lg text-slate-600">
          Finds and negotiates the cheapest suitable hotel in Ontario
        </p>
      </header>

      <section
        aria-labelledby="search-heading"
        className="rounded-xl border border-dashed border-slate-300 bg-white p-6"
      >
        <h2 id="search-heading" className="text-lg font-medium">
          Search
        </h2>
        <p className="mt-2 text-slate-600">
          The search form is coming next: pick an Ontario city, your dates and guests, and the
          agent will phone hotels and negotiate on your behalf.
        </p>
      </section>
    </main>
  );
}
